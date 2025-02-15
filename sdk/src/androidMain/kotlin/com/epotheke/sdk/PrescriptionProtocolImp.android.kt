/****************************************************************************
 * Copyright (C) 2024 ecsec GmbH.
 * All rights reserved.
 * Contact: ecsec GmbH (info@ecsec.de)
 *
 * This file is part of the epotheke SDK.
 *
 * GNU General Public License Usage
 * This file may be used under the terms of the GNU General Public
 * License version 3.0 as published by the Free Software Foundation
 * and appearing in the file LICENSE.GPL included in the packaging of
 * this file. Please review the following information to ensure the
 * GNU General Public License version 3.0 requirements will be met:
 * http://www.gnu.org/copyleft/gpl.html.
 *
 * Other Usage
 * Alternatively, this file may be used in accordance with the terms
 * and conditions contained in a signed written agreement between
 * you and ecsec GmbH.
 *
 ***************************************************************************/

package com.epotheke.sdk

import android.annotation.TargetApi
import android.os.Build
import com.epotheke.erezept.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import java.time.LocalDateTime.now
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

private val logger = KotlinLogging.logger {}
private const val ReceiveTimeoutSeconds = 30L

open class CardLinkProtocolBase(
    protected val ws: WebsocketAndroid,
) : CardLinkProtocol {
    protected val inputChannel = Channel<String>()

    fun registerListener(channelDispatcher: ChannelDispatcher) {
        channelDispatcher.addProtocolChannel(inputChannel)
    }
}

class PrescriptionProtocolImp(
    ws: WebsocketAndroid
) : CardLinkProtocolBase(ws), PrescriptionProtocol {

    @TargetApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun requestPrescriptions(req: RequestPrescriptionList): AvailablePrescriptionLists {
        logger.debug { "Sending data to request ecreipts." }

        ws.send(
            prescriptionJsonFormatter.encodeToString(req)
        )

        val lastTimestampUntilTimeout = now().plusSeconds(ReceiveTimeoutSeconds)
        var duration = ReceiveTimeoutSeconds.seconds
        while (true) {
            try {
                val response = withTimeout(
                    timeout = duration
                ) {
                    inputChannel.receive()
                }
                when (val prescriptionMessage = prescriptionJsonFormatter.decodeFromString<PrescriptionMessage>(response)) {
                    is AvailablePrescriptionLists -> {
                        if (prescriptionMessage.correlationId == req.messageId) {
                            return prescriptionMessage
                        }
                    }

                    is GenericErrorMessage -> {
                        throw PrescriptionProtocolException(prescriptionMessage)
                    }

                    else -> {
                        duration = java.time.Duration.between(now(), lastTimestampUntilTimeout).toKotlinDuration()
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is TimeoutException -> {
                        logger.error { "Timeout during receive" }
                        throw PrescriptionProtocolException(
                            GenericErrorMessage(
                                errorCode = GenericErrorResultType.UNKNOWN_ERROR,
                                errorMessage = "Timeout",
                                correlationId = req.messageId
                            )
                        )
                    }
                }
                logger.error(e) { "Exceptino during receive" }
                throw e
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    override suspend fun selectPrescriptions(lst: SelectedPrescriptionList): SelectedPrescriptionListResponse {
        logger.debug { "Sending data to select ecreipts." }
        ws.send(
            prescriptionJsonFormatter.encodeToString(lst)
        )


        val lastTimestampUntilTimeout = now().plusSeconds(ReceiveTimeoutSeconds)
        var duration = ReceiveTimeoutSeconds.seconds

        while (true) {
            try {
                val response = withTimeout(
                    timeout = duration
                ) {
                    inputChannel.receive()
                }
                when (val prescriptionMessage = prescriptionJsonFormatter.decodeFromString<PrescriptionMessage>(response)) {
                    is SelectedPrescriptionListResponse -> {
                        if (prescriptionMessage.correlationId == lst.messageId) {
                            return prescriptionMessage
                        }
                    }

                    is GenericErrorMessage -> {
                        throw PrescriptionProtocolException(prescriptionMessage)
                    }

                    else -> {
                        duration = java.time.Duration.between(now(), lastTimestampUntilTimeout).toKotlinDuration()
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is TimeoutException -> {
                        logger.error { "Timeout during receive" }
                        throw PrescriptionProtocolException(
                            GenericErrorMessage(
                                errorCode = GenericErrorResultType.UNKNOWN_ERROR,
                                errorMessage = "Timeout",
                                correlationId = lst.messageId
                            )
                        )
                    }
                }
                logger.error(e) { "Exception during receive" }
                throw e
            }
        }
    }

}

