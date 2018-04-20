package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.backend.common.descriptors.allParameters
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor

// TODO: generalize type bridges to support such things as selectors, ignored class method receivers etc.

internal sealed class MethodBridgeParameter

internal sealed class MethodBridgeReceiver : MethodBridgeParameter() {
    object Static : MethodBridgeReceiver()
    object Factory : MethodBridgeReceiver()
    object Instance : MethodBridgeReceiver()
}

internal object MethodBridgeSelector : MethodBridgeParameter()

internal sealed class MethodBridgeValueParameter : MethodBridgeParameter() {
    data class Mapped(val bridge: TypeBridge) : MethodBridgeValueParameter()
    object ErrorOutParameter : MethodBridgeValueParameter()
    data class KotlinResultOutParameter(val bridge: TypeBridge) : MethodBridgeValueParameter()
}

internal data class MethodBridge(
        val returnBridge: ReturnValue,
        val receiver: MethodBridgeReceiver,
        val valueParameters: List<MethodBridgeValueParameter>
) {

    sealed class ReturnValue {
        object Void : ReturnValue()
        object HashCode : ReturnValue()
        data class Mapped(val bridge: TypeBridge) : ReturnValue()
        sealed class Instance : ReturnValue() {
            object InitResult : Instance()
            object FactoryResult : Instance()
        }

        sealed class WithError : ReturnValue() {
            object Success : WithError()
            data class RefOrNull(val successBridge: ReturnValue) : WithError()
        }
    }

    val paramBridges: List<MethodBridgeParameter> =
            listOf(receiver) + MethodBridgeSelector + valueParameters

    // TODO: it is not exactly true in potential future cases.
    val isInstance: Boolean get() = when (receiver) {
        MethodBridgeReceiver.Static,
        MethodBridgeReceiver.Factory -> false

        MethodBridgeReceiver.Instance -> true
    }
}

internal fun MethodBridge.valueParametersAssociated(
        descriptor: FunctionDescriptor
): List<Pair<MethodBridgeValueParameter, ParameterDescriptor?>> {
    val kotlinParameters = descriptor.allParameters.iterator()
    val skipFirstKotlinParameter = when (this.receiver) {
        MethodBridgeReceiver.Static -> false
        MethodBridgeReceiver.Factory, MethodBridgeReceiver.Instance -> true
    }
    if (skipFirstKotlinParameter) {
        kotlinParameters.next()
    }

    return this.valueParameters.map {
        when (it) {
            is MethodBridgeValueParameter.Mapped -> it to kotlinParameters.next()

            is MethodBridgeValueParameter.ErrorOutParameter,
            is MethodBridgeValueParameter.KotlinResultOutParameter -> it to null
        }
    }.also { assert(!kotlinParameters.hasNext()) }
}

internal fun MethodBridge.parametersAssociated(
        descriptor: FunctionDescriptor
): List<Pair<MethodBridgeParameter, ParameterDescriptor?>> {
    val kotlinParameters = descriptor.allParameters.iterator()

    return this.paramBridges.map {
        when (it) {
            is MethodBridgeValueParameter.Mapped, MethodBridgeReceiver.Instance ->
                it to kotlinParameters.next()

            MethodBridgeReceiver.Static, MethodBridgeSelector, MethodBridgeValueParameter.ErrorOutParameter,
            is MethodBridgeValueParameter.KotlinResultOutParameter ->
                it to null

            MethodBridgeReceiver.Factory -> {
                kotlinParameters.next()
                it to null
            }
        }
    }.also { assert(!kotlinParameters.hasNext()) }
}
