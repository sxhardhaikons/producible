package com.sxhardha.producible_processor

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.sxhardha.producible.Producible
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import java.io.File
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@AutoService(Processor::class)
class ProducibleProcessor : KotlinAbstractProcessor() {

    private val producibleAnnotation = Producible::class.java

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(producibleAnnotation)
            .asSequence()
            .map { it as TypeElement }
            .forEach { annotatedElement ->
                if (annotatedElement.kind == ElementKind.CLASS) {
                    if (annotatedElement.superclass.asTypeName().toString().equals(
                            "androidx.lifecycle.ViewModel",
                            false
                        )
                    ) {
                        val pack = elementUtils.getPackageOf(annotatedElement).toString()
                        val annotatedClassName = annotatedElement.simpleName.toString()
                        produceViewModelFactories(pack, annotatedClassName, annotatedElement)
                    } else {
                        messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "${annotatedElement.simpleName} must extend androidx.lifecycle.ViewModel"
                        )
                    }
                } else {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cannot annotate anything but a class"
                    )
                }
            }
        return false
    }

    private fun produceViewModelFactories(
        pack: String,
        annotatedClassName: String,
        annotatedElement: TypeElement
    ) {
        val kaptKotlinGeneratedDir = options[KOTLIN_DIRECTORY_NAME]
        val metadata = annotatedElement.kotlinMetadata as KotlinClassMetadata
        val protoClass = metadata.data.classProto
        val mainConstructor = protoClass.constructorList.find { it.isPrimary }
        FileSpec.builder(pack, "${annotatedClassName}Factory")
            .addType(
                TypeSpec.classBuilder("${annotatedClassName}Factory")
                    .primaryConstructor(
                        FunSpec.constructorBuilder().apply {
                            mainConstructor?.valueParameterList?.forEach {
                                addParameter(
                                    name = metadata.data.nameResolver.getString(it.name),
                                    type = ClassName.bestGuess(
                                        it.type.extractFullName(metadata.data).replace("`", "")
                                    )
                                ).build()
                            }
                        }.build()
                    ).apply {
                        mainConstructor?.valueParameterList?.forEach {
                            addProperty(
                                PropertySpec.builder(
                                    name = metadata.data.nameResolver.getString(it.name),
                                    type = ClassName.bestGuess(
                                        it.type.extractFullName(metadata.data).replace("`", "")
                                    )
                                ).initializer(metadata.data.nameResolver.getString(it.name)).build()
                            ).build()
                        }
                    }
                    .addSuperinterface(
                        ClassName(
                            "androidx.lifecycle.ViewModelProvider",
                            "Factory"
                        )
                    )
                    .addFunction(
                        FunSpec.builder("create").apply {
                            addModifiers(KModifier.OVERRIDE)
                            addCode(
                                "return ${(annotatedClassName)}${generateConstructorParameters(
                                    mainConstructor,
                                    metadata.data
                                )} as T"
                            )
                            addParameter(
                                name = "modelClass",
                                type = ClassName(
                                    "java.lang",
                                    "Class"
                                )
                            )
                        }.build()
                    ).build()
            )
            .addComment("//Generated by Producible")
            .build()
            .writeTo(File(kaptKotlinGeneratedDir, "${annotatedClassName}Factory"))
    }

    private fun generateConstructorParameters(
        mainConstructor: ProtoBuf.Constructor?,
        data: ClassData
    ): String {
        var startOfConstructor = "("
        mainConstructor?.valueParameterList?.forEach {
            startOfConstructor += ", ${data.nameResolver.getString(it.name)}"
        }
        startOfConstructor += ")"

        return startOfConstructor
    }

    override fun getSupportedAnnotationTypes(): Set<String> =
        setOf(producibleAnnotation.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    companion object {
        const val KOTLIN_DIRECTORY_NAME = "kapt.kotlin.generated"
    }
}