package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.ANVIL_SUBCOMPONENT_SUFFIX
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.PARENT_COMPONENT
import com.squareup.anvil.compiler.SUBCOMPONENT_FACTORY
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.internal.testing.extends
import com.squareup.anvil.compiler.internal.testing.packageName
import com.squareup.anvil.compiler.internal.testing.use
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.subcomponentInterface
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Test
import javax.inject.Singleton
import kotlin.test.assertFailsWith

class ContributesSubcomponentHandlerGeneratorTest {

  @Test fun `there is a subcomponent generated for a @MergeComponent`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a subcomponent generated for a @MergeSubcomponent`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeSubcomponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeSubcomponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test
  fun `there is a subcomponent generated for a @MergeInterfaces and the parent component is added to the interface`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.compat.MergeInterfaces
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeInterfaces(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)

      assertThat(
        componentInterface extends subcomponentInterface
          .anvilComponent(componentInterface)
          .parentComponentInterface
      ).isTrue()
    }
  }

  @Test fun `there is no subcomponent generated for a @MergeModules`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.compat.MergeModules
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeModules(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)

      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent(componentInterface)
      }
    }
  }

  @Test fun `there is no subcomponent generated for a mismatching scopes`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Int::class)
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)

      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent(componentInterface)
      }
    }
  }

  @Test fun `there is a subcomponent generated for an inner class`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        class Outer {
          @ContributesSubcomponent(Any::class, Unit::class)
          interface SubcomponentInterface
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val subcomponentInterface = classLoader
        .loadClass("com.squareup.test.Outer\$SubcomponentInterface")

      val anvilComponent = subcomponentInterface.anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a subcomponent generated for an inner parent class`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        class Outer {
          @MergeComponent(Unit::class)
          interface ComponentInterface
        }
        
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent(
        classLoader.loadClass("com.squareup.test.Outer\$ComponentInterface")
      )
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a subcomponent generated in a chain of contributed subcomponents`() {
    // This test will exercise multiple rounds of code generation.
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface1
  
        @ContributesSubcomponent(Int::class, parentScope = Any::class)
        interface SubcomponentInterface2
  
        @ContributesSubcomponent(Long::class, parentScope = Int::class)
        interface SubcomponentInterface3
      """.trimIndent()
    ) {
      var parentComponentInterface = componentInterface

      for (index in 1..3) {
        val subcomponentInterface = classLoader
          .loadClass("com.squareup.test.SubcomponentInterface$index")

        val anvilComponent = subcomponentInterface.anvilComponent(parentComponentInterface)
        assertThat(anvilComponent).isNotNull()

        val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
        assertThat(annotation).isNotNull()

        parentComponentInterface = anvilComponent
      }
    }
  }

  @Test fun `there is a subcomponent generated with separate compilations`() {
    val firstCompilationResult = compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface1
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
        import com.squareup.anvil.annotations.ContributesSubcomponent
        
        @ContributesSubcomponent(Unit::class, parentScope = Int::class)
        interface SubcomponentInterface2
        
        @MergeComponent(Int::class)
        interface ComponentInterface
      """.trimIndent(),
      previousCompilationResult = firstCompilationResult
    ) {
      val anvilComponent = classLoader
        .loadClass("com.squareup.test.SubcomponentInterface2")
        .anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Unit::class)
    }
  }

  @Test fun `Dagger modules can be added manually`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Module

        @Module
        object DaggerModule1
  
        @ContributesSubcomponent(
          scope = Any::class, 
          parentScope = Unit::class,
          modules = [DaggerModule1::class]
        )
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val annotation = subcomponentInterface.anvilComponent(componentInterface)
        .getAnnotation(MergeSubcomponent::class.java)

      assertThat(annotation.modules.toList()).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `Dagger modules can be added manually with multiple compilations`() {
    val firstCompilationResult = compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import dagger.Module

        @Module
        object DaggerModule1
  
        @ContributesSubcomponent(
          scope = Any::class, 
          parentScope = Unit::class,
          modules = [DaggerModule1::class]
        )
        interface SubcomponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent(),
      previousCompilationResult = firstCompilationResult
    ) {
      val annotation = subcomponentInterface.anvilComponent(componentInterface)
        .getAnnotation(MergeSubcomponent::class.java)

      assertThat(annotation.modules.toList()).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `Dagger modules, component interfaces and bindings can be excluded`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Module

        @Module
        @ContributesTo(Any::class)
        object DaggerModule1

        @ContributesTo(Any::class)
        interface ContributingInterface
  
        @ContributesBinding(Any::class)
        interface SecondContributingInterface : CharSequence
  
        @ContributesSubcomponent(
          scope = Any::class, 
          parentScope = Unit::class,
          exclude = [
            DaggerModule1::class, 
            ContributingInterface::class, 
            SecondContributingInterface::class
          ]
        )
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val annotation = subcomponentInterface.anvilComponent(componentInterface)
        .getAnnotation(MergeSubcomponent::class.java)

      assertThat(annotation.exclude.toList()).containsExactly(
        daggerModule1.kotlin, contributingInterface.kotlin, secondContributingInterface.kotlin
      )
    }
  }

  @Test
  fun `Dagger modules, component interfaces and bindings can be excluded with multiple compilations`() {
    val firstCompilationResult = compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module

        @Module
        @ContributesTo(Any::class)
        object DaggerModule1

        @ContributesTo(Any::class)
        interface ContributingInterface
  
        @ContributesBinding(Any::class)
        interface SecondContributingInterface : CharSequence
  
        @ContributesSubcomponent(
          scope = Any::class, 
          parentScope = Unit::class,
          exclude = [
            DaggerModule1::class, 
            ContributingInterface::class, 
            SecondContributingInterface::class
          ]
        )
        interface SubcomponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent

        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent(),
      previousCompilationResult = firstCompilationResult
    ) {
      val annotation = subcomponentInterface.anvilComponent(componentInterface)
        .getAnnotation(MergeSubcomponent::class.java)

      assertThat(annotation.exclude.toList()).containsExactly(
        daggerModule1.kotlin, contributingInterface.kotlin, secondContributingInterface.kotlin
      )
    }
  }

  @Test
  fun `there is a parent component interface automatically generated without declaring one explicitly`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent =
        subcomponentInterface.anvilComponent(componentInterface).parentComponentInterface
      assertThat(parentComponent).isNotNull()

      assertThat(parentComponent.declaredMethods.single().returnType)
        .isEqualTo(subcomponentInterface.anvilComponent(componentInterface))

      assertThat(componentInterface extends parentComponent).isTrue()
    }
  }

  @Test
  fun `the parent component interface extends a manually declared component interface with the same scope`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
            fun integer(): Int
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent =
        subcomponentInterface.anvilComponent(componentInterface).parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val createComponentFunction = parentComponent.declaredMethods.single()
      assertThat(createComponentFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent(componentInterface))
      assertThat(createComponentFunction.name)
        .isEqualTo("createComponent")

      assertThat(
        parentComponent extends subcomponentInterface.anyParentComponentInterface
      ).isTrue()

      assertThat(componentInterface extends parentComponent).isTrue()
    }
  }

  @Test
  fun `the parent component interface extends a manually declared component interface with the same scope with multiple compilations`() {
    val firstCompilationResult = compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
            fun integer(): Int
          }
        }
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
  
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """,
      previousCompilationResult = firstCompilationResult
    ) {
      val parentComponent =
        subcomponentInterface.anvilComponent(componentInterface).parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val createComponentFunction = parentComponent.declaredMethods.single()
      assertThat(createComponentFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent(componentInterface))
      assertThat(createComponentFunction.name)
        .isEqualTo("createComponent")

      assertThat(
        parentComponent extends subcomponentInterface.anyParentComponentInterface
      ).isTrue()

      assertThat(componentInterface extends parentComponent).isTrue()
    }
  }

  @Test
  fun `the parent component interface does not extend a manually declared component interface with a different scope`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Int::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent =
        subcomponentInterface.anvilComponent(componentInterface).parentComponentInterface
      assertThat(
        parentComponent extends subcomponentInterface.anyParentComponentInterface
      ).isFalse()
    }
  }

  @Test
  fun `Dagger generates the real component and subcomponent and they can be instantiated through the component interfaces`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Module
        import dagger.Provides

        @ContributesTo(Any::class)
        @Module
        object DaggerModule {
          @Provides fun provideInteger(): Int = 5
        }
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
          }
          
          fun integer(): Int
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """,
      enableDaggerAnnotationProcessor = true
    ) {
      val daggerComponent = componentInterface.daggerComponent.declaredMethods
        .single { it.name == "create" }
        .invoke(null)

      // Note that there are no declared methods, only inherited methods.
      assertThat(componentInterface.declaredMethods.toList()).isEmpty()

      // There are two methods: one from AnyParentComponent and one from the generated component
      // interface. Both show up in the reflection APIs.
      val subcomponent = componentInterface.methods
        .first { it.name == "createComponent" }
        .invoke(daggerComponent)

      val int = subcomponent::class.java.declaredMethods
        .single { it.name == "integer" }
        .use { it.invoke(subcomponent) as Int }

      assertThat(int).isEqualTo(5)
    }
  }

  @Test fun `contributed subcomponents can be excluded with @MergeComponent`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.MergeComponent

      @ContributesSubcomponent(Unit::class, parentScope = Any::class)
      interface SubcomponentInterface

      @MergeComponent(
          scope = Any::class,
          exclude = [SubcomponentInterface::class]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)

      // Fails because the component is never generated.
      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent(componentInterface)
      }
      assertThat(componentInterface.interfaces).isEmpty()
    }
  }

  @Test fun `contributed subcomponents can be excluded with @MergeSubcomponent`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.MergeSubcomponent

      @ContributesSubcomponent(Unit::class, parentScope = Any::class)
      interface SubcomponentInterface

      @MergeSubcomponent(
          scope = Any::class,
          exclude = [SubcomponentInterface::class]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)

      // Fails because the component is never generated.
      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent(componentInterface)
      }
      assertThat(componentInterface.interfaces).isEmpty()
    }
  }

  @Test fun `contributed subcomponents can be excluded with @MergeModules`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Unit::class, parentScope = Any::class)
      interface SubcomponentInterface

      @MergeModules(
          scope = Any::class,
          exclude = [SubcomponentInterface::class]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)

      // Fails because the component is never generated.
      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent(componentInterface)
      }
    }
  }

  @Test fun `contributed subcomponents can be excluded in one component but not the other`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.MergeComponent

      @ContributesSubcomponent(Unit::class, parentScope = Any::class)
      interface SubcomponentInterface

      @MergeComponent(
        scope = Any::class,
        exclude = [SubcomponentInterface::class]
      )
      interface ComponentInterface

      @MergeComponent(
        scope = Any::class,
      )
      interface ContributingInterface
      """
    ) {
      // Fails because the component is never generated.
      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent(componentInterface)
      }

      assertThat(
        contributingInterface extends
          subcomponentInterface
            .anvilComponent(contributingInterface)
            .parentComponentInterface
      ).isTrue()
    }
  }

  @Test fun `contributed subcomponents can be excluded only with a matching scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.MergeComponent

      @ContributesSubcomponent(Unit::class, parentScope = Int::class)
      interface SubcomponentInterface

      @MergeComponent(
          scope = Any::class,
          exclude = [SubcomponentInterface::class]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scope kotlin.Any wants to exclude " +
          "com.squareup.test.SubcomponentInterface, but the excluded class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test
  fun `the parent component interface can return a factory`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createFactory(): ComponentFactory
          }

          @Factory
          interface ComponentFactory {
            fun createComponent(): SubcomponentInterface
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val createFactoryFunction = subcomponentInterface.anvilComponent(componentInterface)
        .parentComponentInterface
        .declaredMethods
        .single()

      assertThat(createFactoryFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent(componentInterface).generatedFactory)
      assertThat(createFactoryFunction.name).isEqualTo("createFactory")

      assertThat(
        subcomponentInterface.anvilComponent(componentInterface).generatedFactory extends
          subcomponentInterface.componentFactory
      ).isTrue()
    }
  }

  @Test
  fun `a factory can be an abstract class`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createFactory(): ComponentFactory
          }

          @Factory
          abstract class ComponentFactory {
            abstract fun createComponent(): SubcomponentInterface
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val createFactoryFunction = subcomponentInterface.anvilComponent(componentInterface)
        .parentComponentInterface
        .declaredMethods
        .single()

      assertThat(createFactoryFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent(componentInterface).generatedFactory)
      assertThat(createFactoryFunction.name).isEqualTo("createFactory")

      assertThat(
        subcomponentInterface.anvilComponent(componentInterface).generatedFactory extends
          subcomponentInterface.componentFactory
      ).isTrue()
    }
  }

  @Test fun `the generated parent component interface returns the factory if one is present`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          interface ComponentFactory {
            fun createComponent(): SubcomponentInterface
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent =
        subcomponentInterface.anvilComponent(componentInterface).parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val createFactoryFunction = parentComponent.declaredMethods.single()
      assertThat(createFactoryFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent(componentInterface).generatedFactory)
      assertThat(createFactoryFunction.name).isEqualTo("createComponentFactory")

      assertThat(
        subcomponentInterface.anvilComponent(componentInterface).generatedFactory extends
          subcomponentInterface.componentFactory
      ).isTrue()
    }
  }

  @Test fun `Dagger generates the real component and subcomponent with a factory`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.BindsInstance

        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createFactory(): ComponentFactory
          }

          @Factory
          interface ComponentFactory {
            fun createComponent(
              @BindsInstance integer: Int
            ): SubcomponentInterface
          }
          
          fun integer(): Int
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """,
      enableDaggerAnnotationProcessor = true
    ) {
      val daggerComponent = componentInterface.daggerComponent.declaredMethods
        .single { it.name == "create" }
        .invoke(null)

      // Note that there are no declared methods, only inherited methods.
      assertThat(componentInterface.declaredMethods.toList()).isEmpty()

      // There are two methods: one from AnyParentComponent and one from the generated component
      // interface. Both show up in the reflection APIs.
      val factory = componentInterface.methods
        .first { it.name == "createFactory" }
        .invoke(daggerComponent)

      val subcomponent = factory::class.java.declaredMethods
        .single { it.returnType == subcomponentInterface }
        .use { it.invoke(factory, 5) }

      val int = subcomponent::class.java.declaredMethods
        .single { it.name == "integer" }
        .use { it.invoke(subcomponent) as Int }

      assertThat(int).isEqualTo(5)
    }
  }

  @Test
  fun `the generated factory can be injected`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.BindsInstance
        import javax.inject.Inject 

        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          interface ComponentFactory {
            fun createComponent(
              @BindsInstance integer: Int
            ): SubcomponentInterface
          }
          
          fun integer(): Int
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface {
          fun testClass(): TestClass
        }

        class TestClass @Inject constructor(val factory: SubcomponentInterface.ComponentFactory)
      """,
      enableDaggerAnnotationProcessor = true
    ) {
      val daggerComponent = componentInterface.daggerComponent.declaredMethods
        .single { it.name == "create" }
        .invoke(null)

      val testClassInstance = componentInterface.declaredMethods
        .single { it.name == "testClass" }
        .invoke(daggerComponent)

      val factory = testClassInstance::class.java.declaredMethods
        .single { it.name == "getFactory" }
        .invoke(testClassInstance)

      val subcomponent = factory::class.java.declaredMethods
        .single { it.returnType == subcomponentInterface }
        .use { it.invoke(factory, 5) }

      val int = subcomponent::class.java.declaredMethods
        .single { it.name == "integer" }
        .use { it.invoke(subcomponent) as Int }

      assertThat(int).isEqualTo(5)
    }
  }

  @Test fun `the generated subcomponent contains the same scope annotation`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
        import javax.inject.Singleton
  
        @ContributesSubcomponent(Any::class, Unit::class)
        @Singleton
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(Singleton::class.java)
      assertThat(annotation).isNotNull()
    }
  }

  @Test fun `the generated subcomponent contains the same scope annotation - custom scope`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
        import javax.inject.Scope
        import javax.inject.Singleton
        import kotlin.reflect.KClass
        
        @Scope
        @Retention(AnnotationRetention.RUNTIME)
        annotation class SingleIn(val clazz: KClass<*>)
  
        @ContributesSubcomponent(Any::class, Unit::class)
        @SingleIn(Any::class)
        @Singleton
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent(componentInterface)
      assertThat(anvilComponent).isNotNull()

      val singleIn = classLoader.loadClass("com.squareup.test.SingleIn")
        .asSubclass(Annotation::class.java)

      val annotation = anvilComponent.getAnnotation(singleIn)
      assertThat(annotation).isNotNull()
      assertThat(anvilComponent.getAnnotation(Singleton::class.java)).isNotNull()

      val singleInClass = singleIn.declaredMethods.single().invoke(annotation)
      assertThat(singleInClass).isEqualTo(Any::class.java)
    }
  }

  @Test fun `subcomponent can be contributed and bindings replaced in a 2nd compilation`() {
    // This test simulates a compilation in the main source set and test/androidTest source set,
    // where contributed subcomponents are generated a second time for the test components. This
    // test ensures that there are no duplicate generated classes.
    val firstResult = compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Module
        import dagger.Provides

        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface1

        @ContributesSubcomponent(Int::class, parentScope = Any::class)
        interface SubcomponentInterface2 {
          fun integer(): Int
        }

        @ContributesTo(Int::class)
        @Module
        object DaggerModule1 {
          @Provides fun provideIntegerFive(): Int = 5
        }

        @MergeComponent(Unit::class)
        interface ComponentInterface1
      """,
      enableDaggerAnnotationProcessor = true
    ) {
      val daggerComponent = componentInterface1.daggerComponent.declaredMethods
        .single { it.name == "create" }
        .invoke(null)

      val subcomponent1 = componentInterface1.methods
        .single()
        .invoke(daggerComponent)

      val subcomponent2 = subcomponent1::class.java.declaredMethods
        .single()
        .use { it.invoke(subcomponent1) }

      val int = subcomponent2::class.java.declaredMethods
        .single { it.name == "integer" }
        .use { it.invoke(subcomponent2) as Int }

      assertThat(int).isEqualTo(5)
    }

    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Module
        import dagger.Provides

        @ContributesTo(Int::class, replaces = [DaggerModule1::class])
        @Module
        object DaggerModule2 {
          @Provides fun provideIntegerSix(): Int = 6
          @Provides fun provideLongSeven(): Long = 7L
        }

        @ContributesTo(Int::class)
        interface ContributedComponentInterface {
          fun getLong(): Long
        }

        @MergeComponent(Unit::class)
        interface ComponentInterface2
      """,
      enableDaggerAnnotationProcessor = true,
      previousCompilationResult = firstResult
    ) {
      val daggerComponent = componentInterface2.daggerComponent.declaredMethods
        .single { it.name == "create" }
        .invoke(null)

      val subcomponent1 = componentInterface2.methods
        .single()
        .invoke(daggerComponent)

      val subcomponent2 = subcomponent1::class.java.declaredMethods
        .single()
        .use { it.invoke(subcomponent1) }

      val int = subcomponent2::class.java.declaredMethods
        .single { it.name == "integer" }
        .use { it.invoke(subcomponent2) as Int }

      assertThat(int).isEqualTo(6)

      val long = subcomponent2::class.java.methods
        .single { it.name == "getLong" }
        .use { it.invoke(subcomponent2) as Long }

      assertThat(long).isEqualTo(7L)
    }
  }

  @Test fun `contributed subcomponent parent interfaces are merged with the right component`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.MergeComponent

      @ContributesSubcomponent(Unit::class, parentScope = Any::class)
      interface SubcomponentInterface

      @MergeComponent(Any::class)
      interface ComponentInterface1

      @MergeComponent(Any::class)
      interface ComponentInterface2
      """
    ) {
      val parentComponentInterface1 = subcomponentInterface
        .anvilComponent(componentInterface1)
        .parentComponentInterface
      val parentComponentInterface2 = subcomponentInterface
        .anvilComponent(componentInterface2)
        .parentComponentInterface

      assertThat(componentInterface1 extends parentComponentInterface1).isTrue()
      assertThat(componentInterface1 extends parentComponentInterface2).isFalse()

      assertThat(componentInterface2 extends parentComponentInterface2).isTrue()
      assertThat(componentInterface2 extends parentComponentInterface1).isFalse()
    }
  }

  @Test fun `contributed subcomponent class names are compacted`() {
    // This test would fail when javac runs during annotation processing with the class names we
    // originally generated. We now encode the parent class name in the package rather than the
    // class name. Then Dagger won't create too long nested class names for subcomponents. See
    // https://github.com/google/dagger/issues/421
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent

        @ContributesSubcomponent(Int::class, parentScope = Any::class)
        interface SubcomponentInterface

        @MergeComponent(Unit::class)
        interface ComponentInterface
      """,
      """
        package com.squareup.test.superlongpackagename.superlongpackagename.superlongpackagename.superlongpackagename

        import com.squareup.anvil.annotations.ContributesSubcomponent

        @ContributesSubcomponent(scope = Any::class, parentScope = Unit::class)
        interface SubcomponentInterfacewithVeryVeryVeryVeryVeryVeryVeryLongName
      """,
      enableDaggerAnnotationProcessor = true
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  // E.g. anvil.component.com.squareup.test.componentinterface.SubcomponentInterfaceA
  private fun Class<*>.anvilComponent(parent: Class<*>): Class<*> {
    val packageName = parent.packageName()

    val packagePrefix = if (packageName.startsWith(COMPONENT_PACKAGE_PREFIX)) {
      ""
    } else {
      "$COMPONENT_PACKAGE_PREFIX."
    }

    val packageSuffix = generateSequence(parent) { it.enclosingClass }
      .toList()
      .reversed()
      .joinToString(separator = ".") { it.simpleName }
      .lowercase()

    val className = generateSequence(this) { it.enclosingClass }
      .toList()
      .reversed()
      .joinToString(separator = "_") { it.simpleName }

    return classLoader.loadClass(
      "$packagePrefix$packageName$packageSuffix.$className$ANVIL_SUBCOMPONENT_SUFFIX"
    )
  }

  private val Class<*>.anyParentComponentInterface: Class<*>
    get() = classLoader.loadClass("$canonicalName\$AnyParentComponent")

  private val Class<*>.parentComponentInterface: Class<*>
    get() = classLoader.loadClass("$canonicalName\$$PARENT_COMPONENT")

  private val Class<*>.generatedFactory: Class<*>
    get() = classLoader.loadClass("$canonicalName\$$SUBCOMPONENT_FACTORY")

  private val Class<*>.componentFactory: Class<*>
    get() = classLoader.loadClass("$canonicalName\$ComponentFactory")

  private val Class<*>.daggerComponent: Class<*>
    get() = classLoader.loadClass("$packageName.Dagger$simpleName")

  private val Result.componentInterface1: Class<*>
    get() = classLoader.loadClass("com.squareup.test.ComponentInterface1")

  private val Result.componentInterface2: Class<*>
    get() = classLoader.loadClass("com.squareup.test.ComponentInterface2")
}
