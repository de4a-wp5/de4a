package eu.de4a.connector.api;

import java.io.InputStream;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;

/**
 * Response service API interface including
 * Swagger OpenAPI definitions
 *
 */
public interface ResponseAPI {

//    @ApiOperation(value = "Receive evidence response message",
//            consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
//    @ApiImplicitParams({
//        @ApiImplicitParam(name = "request", required = true,
//            dataType = "eu.de4a.iem.core.jaxb.common.ResponseExtractMultiEvidenceType",
//            paramType = "body")
//    })
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Message processed successfully",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class))),
//            @ApiResponse(responseCode = "404", description = "Bad request - Review the input data",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class))),
//            @ApiResponse(responseCode = "500", description = "Something went wrong - Check the response content",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class)))
//    })
     ResponseEntity<byte[]> responseEvidence(@Valid /* @ApiParam(hidden = true) */ InputStream request);

//	@ApiOperation(value = "Receive response message for Subscription & Notification pattern",
//            consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
//    @ApiImplicitParams({
//        @ApiImplicitParam(name = "request", required = true,
//            dataType = "eu.de4a.iem.core.jaxb.common.ResponseEventSubscriptionType",
//            paramType = "body")
//    })
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Message processed successfully",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class))),
//            @ApiResponse(responseCode = "404", description = "Bad request - Review the input data",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class))),
//            @ApiResponse(responseCode = "500", description = "Something went wrong - Check the response content",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class)))
//    })
      ResponseEntity<byte[]> responseEventSubscription(@Valid /* @ApiParam(hidden = true) */ InputStream request);

//	@ApiOperation(value = "Receive RedirectUser message - USI pattern",
//            consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
//    @ApiImplicitParams({
//        @ApiImplicitParam(name = "request", required = true,
//            dataType = "eu.de4a.iem.jaxb.common.types.RedirectUserType",
//            paramType = "body")
//    })
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Message processed successfully",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class))),
//            @ApiResponse(responseCode = "404", description = "Bad request - Review the input data",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class))),
//            @ApiResponse(responseCode = "500", description = "Something went wrong - Check the response content",
//                    content = @Content(schema = @Schema(implementation = ResponseErrorType.class)))
//    })
      ResponseEntity<byte[]> redirectUserUsi(@Valid /* @ApiParam(hidden = true) */ InputStream request);
}
