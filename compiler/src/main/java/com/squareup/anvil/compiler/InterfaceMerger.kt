package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.internal.annotation
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.getAllSuperTypes
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.requireClassId
import com.squareup.anvil.compiler.internal.scope
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.EffectiveVisibility.Public
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class InterfaceMerger(
  private val classScanner: ClassScanner
) : SyntheticResolveExtension {
  override fun addSyntheticSupertypes(
    thisDescriptor: ClassDescriptor,
    supertypes: MutableList<KotlinType>
  ) {
    val mergeAnnotation = thisDescriptor.annotationOrNull(mergeComponentFqName)
      ?: thisDescriptor.annotationOrNull(mergeSubcomponentFqName)
      ?: thisDescriptor.annotationOrNull(mergeInterfacesFqName)

    if (mergeAnnotation == null) {
      super.addSyntheticSupertypes(thisDescriptor, supertypes)
      return
    }

    val module = thisDescriptor.module

    val scope = mergeAnnotation.scope(module)
    val scopeFqName = scope.fqNameSafe

    if (!DescriptorUtils.isInterface(thisDescriptor)) {
      throw AnvilCompilationException(thisDescriptor, "Dagger components must be interfaces.")
    }

    val classes = classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
        annotation = contributesToFqName,
        scope = scopeFqName
      )
      .filter {
        DescriptorUtils.isInterface(it) && it.annotationOrNull(daggerModuleFqName) == null
      }
      .mapNotNull {
        val contributeAnnotation =
          it.annotationOrNull(contributesToFqName, scope = scopeFqName)
            ?: return@mapNotNull null
        it to contributeAnnotation
      }
      .onEach { (classDescriptor, _) ->
        if (classDescriptor.effectiveVisibility() !is Public) {
          throw AnvilCompilationException(
            classDescriptor,
            "${classDescriptor.fqNameSafe} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported."
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val replacedClasses = classes
      .flatMap { (classDescriptor, contributeAnnotation) ->
        contributeAnnotation.replaces(classDescriptor.module)
          .onEach { classDescriptorForReplacement ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!DescriptorUtils.isInterface(classDescriptorForReplacement)) {
              throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} wants to replace " +
                  "${classDescriptorForReplacement.fqNameSafe}, but the class being " +
                  "replaced is not an interface."
              )
            }

            val contributesToAnnotation = classDescriptorForReplacement
              .annotationOrNull(contributesToFqName)
            val contributesBindingAnnotation = classDescriptorForReplacement
              .annotationOrNull(contributesBindingFqName)
            val contributesMultibindingAnnotation = classDescriptorForReplacement
              .annotationOrNull(contributesMultibindingFqName)

            // Verify that the replaced classes use the same scope.
            val scopeOfReplacement = contributesToAnnotation?.scope(module)
              ?: contributesBindingAnnotation?.scope(module)
              ?: contributesMultibindingAnnotation?.scope(module)
              ?: throw AnvilCompilationException(
                classDescriptor,
                "Could not determine the scope of the replaced class " +
                  "${classDescriptorForReplacement.fqNameSafe}."
              )

            if (scopeOfReplacement.fqNameSafe != scopeFqName) {
              throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} with scope $scopeFqName wants to replace " +
                  "${classDescriptorForReplacement.fqNameSafe} with scope " +
                  "${scopeOfReplacement.fqNameSafe}. The replacement must use the same " +
                  "scope."
              )
            }
          }
          .map { it.fqNameSafe }
      }
      .toSet()

    val excludedClasses = (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)
      ?.value
      ?.map { it.argumentType(module).requireClassDescriptor() }
      ?.filter { DescriptorUtils.isInterface(it) }
      ?.map { classDescriptorForExclusion ->
        val contributesToAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesToFqName)
        val contributesBindingAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesBindingFqName)
        val contributesMultibindingAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesMultibindingFqName)
        val contributesSubcomponentAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesSubcomponentFqName)

        // Verify that the replaced classes use the same scope.
        val scopeOfExclusion = contributesToAnnotation?.scope(module)
          ?: contributesBindingAnnotation?.scope(module)
          ?: contributesMultibindingAnnotation?.scope(module)
          ?: contributesSubcomponentAnnotation?.parentScope(module)
          ?: throw AnvilCompilationException(
            thisDescriptor,
            "Could not determine the scope of the excluded class " +
              "${classDescriptorForExclusion.fqNameSafe}."
          )

        if (scopeOfExclusion.fqNameSafe != scopeFqName) {
          throw AnvilCompilationException(
            thisDescriptor,
            "${thisDescriptor.fqNameSafe} with scope $scopeFqName wants to exclude " +
              "${classDescriptorForExclusion.fqNameSafe} with scope " +
              "${scopeOfExclusion.fqNameSafe}. The exclusion must use the same scope."
          )
        }

        classDescriptorForExclusion.fqNameSafe
      }
      ?: emptyList()

    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes.getAllSuperTypes()
        .toList()
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationException(
          classDescriptor = thisDescriptor,
          message = "${thisDescriptor.name} excludes types that it implements or extends. " +
            "These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString()}"
        )
      }
    }

    @Suppress("ConvertCallChainIntoSequence")
    val contributedClasses = classes
      .map { it.first }
      .filterNot {
        val fqName = it.fqNameSafe
        replacedClasses.contains(fqName) || excludedClasses.contains(fqName)
      }
      .plus(findContributedSubcomponentParentInterfaces(thisDescriptor, scopeFqName, module))
      // Avoids an error for repeated interfaces.
      .distinctBy { it.fqNameSafe }
      .map { it.defaultType }

    supertypes += contributedClasses
    super.addSyntheticSupertypes(thisDescriptor, supertypes)
  }

  private fun findContributedSubcomponentParentInterfaces(
    descriptor: ClassDescriptor,
    scope: FqName,
    module: ModuleDescriptor
  ): Sequence<ClassDescriptor> {
    return classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_SUBCOMPONENTS_PACKAGE_PREFIX,
        annotation = contributesSubcomponentFqName,
        scope = null
      )
      .filter {
        it.annotation(contributesSubcomponentFqName).parentScope(module).fqNameSafe == scope
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.requireClassId()
          .generatedAnvilSubcomponent(descriptor.requireClassId())
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .classDescriptorOrNull(module)
      }
  }
}
