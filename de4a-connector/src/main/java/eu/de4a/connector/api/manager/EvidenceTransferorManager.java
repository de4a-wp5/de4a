package eu.de4a.connector.api.manager;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.commons.error.level.EErrorLevel;
import com.helger.peppolid.CIdentifier;
import eu.de4a.connector.as4.client.regrep.RegRepTransformer;
import eu.de4a.connector.as4.owner.MessageOwner;
import eu.de4a.connector.as4.owner.MessageResponseOwner;
import eu.de4a.connector.as4.owner.OwnerLocator;
import eu.de4a.connector.client.Client;
import eu.de4a.connector.error.exceptions.ConnectorException;
import eu.de4a.connector.error.exceptions.OwnerException;
import eu.de4a.connector.error.exceptions.ResponseErrorException;
import eu.de4a.connector.error.exceptions.ResponseTransferEvidenceException;
import eu.de4a.connector.error.model.ExternalModuleError;
import eu.de4a.connector.error.model.FamilyErrorType;
import eu.de4a.connector.error.model.LayerError;
import eu.de4a.connector.error.model.MessageKeys;
import eu.de4a.connector.error.utils.ErrorHandlerUtils;
import eu.de4a.connector.error.utils.ResponseErrorFactory;
import eu.de4a.connector.model.OwnerAddresses;
import eu.de4a.connector.model.RequestorRequest;
import eu.de4a.connector.model.smp.NodeInfo;
import eu.de4a.connector.repository.RequestorRequestRepository;
import eu.de4a.connector.service.spring.MessageUtils;
import eu.de4a.exception.MessageException;
import eu.de4a.iem.jaxb.common.types.RequestTransferEvidenceUSIIMDRType;
import eu.de4a.iem.jaxb.common.types.ResponseTransferEvidenceType;
import eu.de4a.iem.xml.de4a.DE4AMarshaller;
import eu.de4a.iem.xml.de4a.IDE4ACanonicalEvidenceType;
import eu.de4a.kafkaclient.DE4AKafkaClient;
import eu.de4a.util.DE4AConstants;
import eu.de4a.util.DOMUtils;
import eu.toop.connector.api.TCIdentifierFactory;
import eu.toop.connector.api.me.outgoing.MEOutgoingException;
import eu.toop.connector.api.rest.TCPayload;

@Component
public class EvidenceTransferorManager extends EvidenceManager {
    private static final Logger logger = LoggerFactory.getLogger (EvidenceTransferorManager.class);

    @Autowired
    private Client client;
    @Autowired
    private OwnerLocator ownerLocator;
    @Autowired
    private RequestorRequestRepository requestorRequestRepository;


    public void queueMessage(MessageOwner request) {
        logger.info("Queued message to be send to the owner - RequestId: {}, DataEvaluatorId: {}, DataOwnerId: {}",
                request.getId(), request.getSenderId(), request.getReceiverId());
        if (logger.isDebugEnabled()) {
            logger.debug(DOMUtils.documentToString(request.getMessage().getOwnerDocument()));
        }
        ResponseTransferEvidenceType responseTransferEvidenceType = null;
        RequestTransferEvidenceUSIIMDRType req = (RequestTransferEvidenceUSIIMDRType) ErrorHandlerUtils
                .conversionDocWithCatching(DE4AMarshaller.drImRequestMarshaller(),
                        request.getMessage().getOwnerDocument(), false, false,
                        new ResponseTransferEvidenceException().withModule(ExternalModuleError.CONNECTOR_DT));

        ConnectorException ex = new OwnerException().withModule(ExternalModuleError.CONNECTOR_DT).withRequest(req)
                .withHttpStatus(HttpStatus.OK);        
        try {
            OwnerAddresses ownerAddress = getOwnerAddress(request.getReceiverId(), ex);
            RequestorRequest requestorReq = new RequestorRequest();
            if (req != null) {
                requestorReq.setCanonicalEvidenceTypeId(req.getCanonicalEvidenceTypeId());
                requestorReq.setDataOwnerId(req.getDataOwner().getAgentUrn());
                responseTransferEvidenceType = (ResponseTransferEvidenceType) client.sendEvidenceRequest(req,
                        ownerAddress.getEndpoint(), false);
                if (responseTransferEvidenceType != null) {
                    Document docResponse = (Document) ErrorHandlerUtils.conversionDocWithCatching(
                            DE4AMarshaller.drImResponseMarshaller(IDE4ACanonicalEvidenceType.NONE),
                            responseTransferEvidenceType, true, false,
                            new ResponseTransferEvidenceException().withModule(ExternalModuleError.CONNECTOR_DT));
                    // TODO if as4 message DT-DR failed, what is the approach. retries?
                    if (!sendResponseMessage(req.getDataEvaluator().getAgentUrn(), req.getCanonicalEvidenceTypeId(),
                            docResponse.getDocumentElement(), DE4AConstants.TAG_EVIDENCE_RESPONSE)) {
                        logger.error("Error sending ResponseTransferEvidence to Data Requestor through AS4 gateway");
                    }
                } else {
                    throw ex.withFamily(FamilyErrorType.CONNECTION_ERROR).withModule(ExternalModuleError.DATA_OWNER)
                            .withMessageArg("Response from owner was empty");
                }
            } else {
                req = (RequestTransferEvidenceUSIIMDRType) ErrorHandlerUtils.conversionDocWithCatching(
                        DE4AMarshaller.drUsiRequestMarshaller(), request.getMessage(), false, true,
                        new ResponseErrorException().withModule(ExternalModuleError.CONNECTOR_DT));
                requestorReq.setCanonicalEvidenceTypeId(req.getCanonicalEvidenceTypeId());
                requestorReq.setDataOwnerId(req.getDataOwner().getAgentUrn());
                client.sendEvidenceRequest(req, ownerAddress.getEndpoint(), true);
            }
            // Save request information
            requestorReq.setIdrequest(request.getId());
            requestorReq.setEvidenceServiceUri(request.getReceiverId());
            requestorReq.setSenderId(request.getSenderId());
            requestorReq.setDone(false);
            requestorRequestRepository.save(requestorReq);

        } catch (ConnectorException e) {
            responseTransferEvidenceType = (ResponseTransferEvidenceType) ResponseErrorFactory
                    .getHandlerFromClassException(ex.getClass()).buildResponse(ex);

            if (req == null || !sendResponseMessage(request.getSenderId(), req.getCanonicalEvidenceTypeId(),
                    DE4AMarshaller.drImResponseMarshaller(IDE4ACanonicalEvidenceType.NONE)
                            .getAsDocument(responseTransferEvidenceType).getDocumentElement(),
                    DE4AConstants.TAG_EVIDENCE_RESPONSE)) {
                logger.error("Error sending ResponseTransferEvidence to Data Requestor through AS4 gateway");
            }
        }
    }

    public void queueMessageResponse(MessageResponseOwner response) {
        logger.info("Queued response from owner USI pattern - RequestId: {}, DataEvaluatorId: {}, DataOwnerId: {}", 
                response.getId(), response.getDataEvaluatorId(), response.getDataOwnerId());
        if (logger.isDebugEnabled()) {          
            logger.debug(DOMUtils.documentToString(response.getMessage().getOwnerDocument()));
        }
        RequestorRequest usirequest = requestorRequestRepository.findById(response.getId()).orElse(null);
        if (usirequest == null) {
            logger.error("Does not exists any request with ID {}", response.getId());
        } else {
            //TODO if as4 message DT-DR failed, what is the approach. retries?
            if(!sendResponseMessage(usirequest.getSenderId(), usirequest.getCanonicalEvidenceTypeId (), response.getMessage(),
                    DE4AConstants.TAG_EVIDENCE_REQUEST_DT)) {
                logger.error("Error sending RequestForwardEvidence to Data Requestor through AS4 gateway");
            }
        }
    }

    public boolean sendResponseMessage(String sender, String docTypeID, Element message, String tagContentId) {
        String errorMsg;
        NodeInfo nodeInfo = client.getNodeInfo(sender, docTypeID, true,message);
        try {
            String senderId = sender;
            if(sender.contains(TCIdentifierFactory.PARTICIPANT_SCHEME + CIdentifier.URL_SCHEME_VALUE_SEPARATOR)) {
                senderId = sender.replace(TCIdentifierFactory.PARTICIPANT_SCHEME + CIdentifier.URL_SCHEME_VALUE_SEPARATOR, "");
            }
            
            DE4AKafkaClient.send(EErrorLevel.INFO, MessageFormat.format("Sending response message via AS4 gateway - "
                    + "DataEvaluatorId: {0}, Message tag: {1}, CanonicalEvidenceType: {2}", 
                    senderId, tagContentId, docTypeID));
            
            List<TCPayload> payloads = new ArrayList<>();
            TCPayload payload = new TCPayload();
            payload.setContentID(tagContentId);
            payload.setValue(DOMUtils.documentToByte(message.getOwnerDocument()));
            payload.setMimeType("application/xml");
            payloads.add(payload);
            Element requestWrapper = new RegRepTransformer().wrapMessage(message, false);
            as4Client.sendMessage(senderId, nodeInfo, nodeInfo.getDocumentIdentifier(), requestWrapper, payloads, false);
            
            return true;
        } catch (MEOutgoingException e) {
            errorMsg = "Error with as4 gateway comunications: " + e.getMessage();
            
        } catch (MessageException e) {
            errorMsg = "Error building wrapper message: " + e.getMessage();
        }
        DE4AKafkaClient.send(EErrorLevel.ERROR, errorMsg);
        return false;
    }
    
    private OwnerAddresses getOwnerAddress(String dataOwnerId, ConnectorException ex) {
        DE4AKafkaClient.send(EErrorLevel.INFO, MessageFormat.format("Looking for data owner address - "
                + "DataOwnerId: {0}", dataOwnerId));
        
        OwnerAddresses ownerAddress = ownerLocator.lookupOwnerAddress(dataOwnerId);
        if (ownerAddress == null) {
            DE4AKafkaClient.send(EErrorLevel.ERROR,
                    MessageFormat.format("Data owner address not found - DataOwnerId: {0}", dataOwnerId));

            throw ex.withFamily(FamilyErrorType.SAVING_DATA_ERROR).withLayer(LayerError.CONFIGURATION)
                    .withMessageArg(new MessageUtils(MessageKeys.ERROR_OWNER_NOT_FOUND, new Object[] { dataOwnerId }));
        }
        return ownerAddress;
    }

}
