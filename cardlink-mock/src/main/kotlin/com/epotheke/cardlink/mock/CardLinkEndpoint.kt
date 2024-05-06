package com.epotheke.cardlink.mock

import com.epotheke.cardlink.mock.encoding.JsonEncoder
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.codec.http.QueryStringDecoder
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.*
import jakarta.websocket.server.ServerEndpoint
import java.util.Base64
import java.util.UUID


private val logger = KotlinLogging.logger {}

@ApplicationScoped
@ServerEndpoint("/cardlink", subprotocols = ["cardlink"], encoders = [ JsonEncoder::class ])
class CardLinkEndpoint {

    @Inject
    lateinit var objMapper: ObjectMapper

    @OnOpen
    fun onOpen(session: Session, cfg: EndpointConfig) {
        val webSocketId = getWebSocketId(session)
        logger.debug { "New WebSocket connection with ID: $webSocketId." }
    }

    @OnClose 
    fun onClose(session:Session, reason: CloseReason) {
        val webSocketId = getWebSocketId(session)
        logger.debug { "WebSocket connection with ID: $webSocketId was closed." }
    }

    @OnError
    fun onError(session: Session, t: Throwable) {
        val webSocketId = getWebSocketId(session)
        logger.debug(t) { "An error occurred for WebSocket connection with ID: $webSocketId." }
    }

    @OnMessage
    fun onMessage(session: Session, data: String) {
        val payload = objMapper.readValue(data, JsonNode::class.java)

        if (! payload.nodeType.equals(JsonNodeType.ARRAY)) {
            // send error response payload
            logger.debug { "Payload is not from type array." }
        }

        val cardSessionId : String? = if (payload.has(1)) payload.get(1).textValue() else null
        val correlationId : String? = if (payload.has(2)) payload.get(2).textValue() else null

        logger.debug { "New incoming websocket message with cardSessionId '$cardSessionId' and correlationId '$correlationId'." }
        logger.debug { data }

        if (payload.has(0)) {
            val egkEnvelope = objMapper.treeToValue(payload.get(0), EgkEnvelope::class.java)

            logger.debug { "Incoming message with cardSessionId '$cardSessionId' from type '${egkEnvelope.type}'." }

            when (egkEnvelope.type) {
                EgkEnvelopeTypes.REQUEST_SMS_CODE -> {
                    handleRequestSmsCode(egkEnvelope.payload, session)
                }
                EgkEnvelopeTypes.CONFIRM_SMS_CODE -> {
                    handleConfirmSmsCode(egkEnvelope.payload, cardSessionId, session)
                }
                EgkEnvelopeTypes.REGISTER_EGK_ENVELOPE_TYPE -> {
                    handleRegisterEgkPayload(egkEnvelope.payload, session)
                }
                EgkEnvelopeTypes.SEND_APDU_RESPONSE_ENVELOPE -> {
                    handleApduResponse(egkEnvelope.payload, session)
                }
                EgkEnvelopeTypes.TASK_LIST_ERROR_ENVELOPE -> {
                    logger.debug { "Received Tasklist Error Envelope message." }
                }
            }
        }
    }

    private fun handleConfirmSmsCode(payload: String?, cardSessionId: String?, session: Session) {
        if (payload == null) {
            logger.error { "Payload is null." }
            // TODO send error to other participant
        }

        val confirmSmsCodePayload = objMapper.readValue(
            Base64.getDecoder().decode(payload),
            ConfirmSmsCodePayload::class.java
        )

        logger.debug { "Received 'confirmSmsCode' with sms code: '${confirmSmsCodePayload.smsCode}'." }
        logger.debug { "Sending out 'confirmSmsCodeResponse' ..." }

        val confirmSmsCodeResponse = ConfirmSmsCodeResponsePayload("SUCCESS")
        val confirmSmsCodePayloadStr = objMapper.writeValueAsBytes(confirmSmsCodeResponse)
        val confirmSmsCodePayloadBase64 = Base64.getEncoder().encodeToString(confirmSmsCodePayloadStr)

        val confirmSmsCodeResponseEnvelope = EgkEnvelope(EgkEnvelopeTypes.CONFIRM_SMS_CODE_RESPONSE, confirmSmsCodePayloadBase64)
        val confirmSmsCodeResponseEnvelopeJson = objMapper.convertValue(confirmSmsCodeResponseEnvelope, JsonNode::class.java)

        val confirmSmsCodeResponseJson = objMapper.createArrayNode()
        confirmSmsCodeResponseJson.add(confirmSmsCodeResponseEnvelopeJson)
        confirmSmsCodeResponseJson.add(cardSessionId)
        confirmSmsCodeResponseJson.add(UUID.randomUUID().toString())

        session.asyncRemote.sendObject(confirmSmsCodeResponseJson) {
            if (it.exception != null) {
                logger.debug(it.exception) { "Unable to send message." }
            }
        }
    }

    private fun handleRequestSmsCode(payload: String?, session: Session) {
        if (payload == null) {
            logger.error { "Payload is null." }
            // TODO send error to other participant
        }

        val requestSmsCodePayload = objMapper.readValue(
            Base64.getDecoder().decode(payload),
            RequestSmsCodePayload::class.java
        )

        logger.debug { "Received 'requestSmsCode' with senderId '${requestSmsCodePayload.senderId}' and phoneNumber '${requestSmsCodePayload.phoneNumber}'." }
        logger.debug { "Sending SMS out to '${requestSmsCodePayload.phoneNumber}'." }
    }

    fun handleRegisterEgkPayload(payload: String?, session: Session) {
        if (payload == null) {
            logger.error { "Payload is null." }
            // TODO send error to other participant
        }

        val registerEgkPayload = objMapper.readValue(
            Base64.getDecoder().decode(payload),
            RegisterEgkPayload::class.java
        )

        logger.debug { "Send 'SICCT Card inserted Event' to Connector." }
        logger.debug { "Received 'cmdAPDU INTERNAL AUTHENTICATE' from Connector." }

        sendReady(registerEgkPayload, session)
        sendApdu(registerEgkPayload, session)
    }

    private fun sendReady(registerEgkPayload: RegisterEgkPayload, session: Session) {
        val readyEvelope = EgkEnvelope(EgkEnvelopeTypes.READY, null)
        val readyEnvelopeJson = objMapper.convertValue(readyEvelope, JsonNode::class.java)

        val readyJson = objMapper.createArrayNode()
        readyJson.add(readyEnvelopeJson)
        readyJson.add(registerEgkPayload.cardSessionId)
        readyJson.add(UUID.randomUUID().toString())

        session.asyncRemote.sendObject(readyJson) {
            if (it.exception != null) {
                logger.debug(it.exception) { "Unable to send message." }
            }
        }
    }

    private fun sendApdu(registerEgkPayload: RegisterEgkPayload, session: Session) {
        val sendApduPayload = SendApduPayload(registerEgkPayload.cardSessionId, "<BASE64_ENCODED_APDU>")
        val sendApduPayloadStr = objMapper.writeValueAsBytes(sendApduPayload)
        val sendApduPayloadBase64 = Base64.getEncoder().encodeToString(sendApduPayloadStr)

        val sendApduEnvelope = EgkEnvelope(EgkEnvelopeTypes.SEND_APDU_ENVELOPE, sendApduPayloadBase64)
        val sendApduEnvelopeJson = objMapper.convertValue(sendApduEnvelope, JsonNode::class.java)

        val sendApduJson = objMapper.createArrayNode()
        sendApduJson.add(sendApduEnvelopeJson)
        sendApduJson.add(registerEgkPayload.cardSessionId)
        sendApduJson.add(UUID.randomUUID().toString())

        session.asyncRemote.sendObject(sendApduJson) {
            if (it.exception != null) {
                logger.debug(it.exception) { "Unable to send message." }
            }
        }
    }

    private fun handleApduResponse(payload: String?, session: Session) {
        if (payload == null) {
            logger.error { "Payload is null." }
            // TODO send error to other participant
        }

        val sendApduResponsePayload = objMapper.readValue(
            Base64.getDecoder().decode(payload),
            SendApduResponsePayload::class.java
        )

        logger.debug { "Received APDU response payload for card session: '${sendApduResponsePayload.cardSessionId}'." }
        logger.debug { "Send response of INTERNAL AUTHENTICATE to Connector." }
    }

    private fun getWebSocketId(session: Session) : String? {
        val queryString = if (session.queryString.startsWith("?")) session.queryString else "?${session.queryString}"
        val parameters = QueryStringDecoder(queryString).parameters()
        return parameters["token"]?.firstOrNull()
    }
}
