package eu.de4a.connector.api.controller.error;

import eu.de4a.iem.jaxb.common.types.ErrorListType;
import eu.de4a.iem.jaxb.common.types.RequestTransferEvidenceUSIIMDRType;
import eu.de4a.iem.jaxb.common.types.ResponseExtractEvidenceType;
import eu.de4a.iem.xml.de4a.DE4AMarshaller;
import eu.de4a.iem.xml.de4a.DE4AResponseDocumentHelper;
import eu.de4a.iem.xml.de4a.IDE4ACanonicalEvidenceType;
import eu.de4a.util.MessagesUtils;

public class ResponseExtractEvidenceExceptionHandler extends ConnectorExceptionHandler {
    
    @Override
    public Object getResponseError(ConnectorException ex, boolean returnString) {
        ResponseExtractEvidenceType response = buildResponse(ex);
        if(returnString) {
            return DE4AMarshaller.doImResponseMarshaller(IDE4ACanonicalEvidenceType.NONE)
                    .getAsString(response);
        }
        return response;
    }
    
    public ResponseExtractEvidenceType buildResponse(ConnectorException ex) {
        ErrorListType errorList = new ErrorListType();
        String msg = getMessage(ex);
        errorList.addError(DE4AResponseDocumentHelper.createError(ex.buildCode(), msg));
        ResponseExtractEvidenceType response = DE4AResponseDocumentHelper.createResponseExtractEvidence(
                MessagesUtils.transformRequestToOwnerIM((RequestTransferEvidenceUSIIMDRType) ex.getRequest()));
        response.setErrorList(errorList);
        return response;
    }

}