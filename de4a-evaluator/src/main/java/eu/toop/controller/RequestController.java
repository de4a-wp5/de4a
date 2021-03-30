package eu.toop.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import eu.de4a.conn.owner.model.PreviewRequest;
import eu.de4a.exception.MessageException;
import eu.de4a.iem.jaxb.common.types.AgentType;
import eu.de4a.iem.jaxb.common.types.RequestForwardEvidenceType;
import eu.de4a.iem.jaxb.common.types.RequestLookupRoutingInformationType;
import eu.de4a.iem.jaxb.common.types.RequestTransferEvidenceUSIIMDRType;
import eu.de4a.iem.jaxb.common.types.ResponseErrorType;
import eu.de4a.iem.jaxb.common.types.ResponseLookupRoutingInformationType;
import eu.de4a.iem.jaxb.common.types.ResponseTransferEvidenceType;
import eu.de4a.iem.xml.de4a.DE4AMarshaller;
import eu.de4a.iem.xml.de4a.DE4AResponseDocumentHelper;
import eu.de4a.evaluator.model.EvaluatorRequest;
import eu.de4a.evaluator.model.EvaluatorRequestData;
import eu.de4a.evaluator.repository.EvaluatorRequestDataRepository;
import eu.de4a.evaluator.repository.EvaluatorRequestRepository;
import eu.de4a.util.DE4AConstants;
import eu.de4a.util.DOMUtils;
import eu.de4a.util.EvidenceTypeIds;
import eu.de4a.util.XDE4ACanonicalEvidenceType;
import eu.de4a.util.XDE4AMarshaller;
import eu.toop.rest.Client;

@Controller 
@Scope("session")
public class RequestController {
	private static final Logger logger =  LoggerFactory.getLogger (RequestController.class);
	@Autowired
	private Client client;
	@Autowired 
	private EvaluatorRequestRepository evaluatorRequestRepository; 
	@Autowired
	private EvaluatorRequestDataRepository evaluatorRequestDataRepository; 
	@Autowired
	private ResponseManager responseManager; 
	@Value("${de4a.connector.url.requestor.redirect}")
	private String urlRequestorRedirect;
	private String id;
	private RequestTransferEvidenceUSIIMDRType requestEvidencia;
	
	private static final String REDIRECT_ADDR = "redirect:/%s";

	@GetMapping(value = "/")
	public String welcome(Model model) { 
		model.addAttribute("evidenceForm", new RequestLookupRoutingInformationType());
		return "welcome";
	}
	@GetMapping("/greeting")
	public String greetingForm(Model model) { 
		model.addAttribute("userForm", new User());
		return "greeting";
	}
	
	@GetMapping(value = "/redirectOwner")
	public String redirectOwner(Model model) { 
		model.addAttribute("userForm", new User());
		return "redirectOwner";
	}
	
	@PostMapping(value ="/goEvidenceForm")
	public String goEvidenceForm(Model model, @ModelAttribute("evidenceForm") RequestLookupRoutingInformationType lookupRouting,
			HttpServletRequest requesthttp,HttpServletResponse httpServletResponse) {
		User u = new User();		
		try {
			ResponseLookupRoutingInformationType responseRouting = client.getRoutingInfo(lookupRouting);
			Map<String, String> dataOwnerLabels = new HashMap<>();
			responseRouting.getAvailableSources().getSource().forEach(s -> {
				s.getProvisionsItem().getProvisionItem().stream().forEach(i -> {
					dataOwnerLabels.put(i.getDataOwnerId(), i.getDataOwnerPrefLabel());
				});
			});
			model.addAttribute("dataOwnerList", dataOwnerLabels);
		} catch (MessageException e) {
			logger.error("Ha ocurrido un error en la consulta de rounting info", e);
		}
		u.setEvidenceTypeId(lookupRouting.getCanonicalEvidenceTypeId());
		u.setCountry(lookupRouting.getCountryCode());
		model.addAttribute("userForm",u );
		
		if(EvidenceTypeIds.BIRTHCERTIFICATE.toString().equals(lookupRouting.getCanonicalEvidenceTypeId())) {
			return "nacimiento";
		}
		return "dba";
	}
	
	@PostMapping(value = "/greetinggo") 
	public String greetingSubmit(@ModelAttribute("userForm") User user,HttpServletRequest requesthttp,HttpServletResponse httpServletResponse) {
		RequestLookupRoutingInformationType request = new RequestLookupRoutingInformationType();
			request.setDataOwnerId(user.getDataOwnerId());
			request.setCanonicalEvidenceTypeId(user.getEvidenceTypeId());
			request.setCountryCode(user.getCountry());
			
			try {
				//Obtenemos el evidenceSeriviceUri del DR-IDK para incluirlo en la RequestTransferEvidence
				ResponseLookupRoutingInformationType response = client.getRoutingInfo(request);
				if(response.getErrorList() == null) {
					AgentType dataOwner = new AgentType();
					response.getAvailableSources().getSource().stream().forEach(s -> {
						s.getProvisionsItem().getProvisionItem().stream().forEach(p -> {
							dataOwner.setAgentUrn(p.getDataOwnerId());
							dataOwner.setAgentName(p.getDataOwnerPrefLabel());
							dataOwner.setRedirectURL(p.getProvision().getRedirectURL());
						});
					});
				
					requestEvidencia = client.buildRequest(user, dataOwner, user.getEvidenceTypeId());
					var requestMarshaller = DE4AMarshaller.drUsiRequestMarshaller();
					user.setRequest(requestMarshaller.formatted().getAsString(requestEvidencia));
					id=requestEvidencia.getRequestId();  
					return "showRequest";
				} else {
					return String.format(REDIRECT_ADDR, "errorPage.jsp");
				}
			} catch (Exception | MessageException e) {
				return String.format(REDIRECT_ADDR, "errorPage.jsp");
			}
	}
	
	@PostMapping(value = "/requestEvidence") 
	public String sendRequest(Model model, HttpServletRequest requesthttp,HttpServletResponse httpServletResponse,RedirectAttributes redirectAttributes) {  
		try {
			EvaluatorRequest request = new EvaluatorRequest();
			request.setIdrequest(id);
			
			String redirectAddr = String.format(REDIRECT_ADDR, "errorPage.jsp");
			if(!ObjectUtils.isEmpty(requestEvidencia.getDataOwner().getRedirectURL())) {
				//USI pattern
				request.setUsi(true);
				evaluatorRequestRepository.save(request);
				//TODO handle errors
				ResponseErrorType response = client.getEvidenceRequestUSI(requestEvidencia);
				PreviewRequest previewRequest = new PreviewRequest();
				previewRequest.setIdRequest(requestEvidencia.getRequestId());
				previewRequest.setReturnUrl(requestEvidencia.getDataEvaluator().getRedirectURL());
				model.addAttribute("previewRequest", previewRequest);
				model.addAttribute("idRequest", requestEvidencia.getRequestId());
				model.addAttribute("redirectUrl", requestEvidencia.getDataOwner().getRedirectURL());
				redirectAddr = "redirectOwner";
			} else {
				//IM pattern
				request.setUsi(false);
				evaluatorRequestRepository.save(request);
				client.getEvidenceRequestIM(requestEvidencia);
				redirectAttributes.addAttribute("id", id);
				redirectAddr = String.format(REDIRECT_ADDR, "returnPage.jsp");			
			}									
			return redirectAddr;
		} catch (MessageException e) {
			logger.error("Error getting evidence request",e);
			return String.format(REDIRECT_ADDR, "errorPage.jsp");
		}
		
	}

	@GetMapping(value = "/returnEvidence"  )
	public String receiveEvidence(@RequestParam String id,RedirectAttributes redirectAttributes) 
	{   

		   redirectAttributes.addAttribute("id", id);
		return "redirect:/returnPage.jsp";
	}
	
	@PostMapping(value = "/requestForwardEvidence") 
	public @ResponseBody String requestForwardEvidence(@RequestBody String requestForward, HttpServletRequest requesthttp, 
			HttpServletResponse httpServletResponse,RedirectAttributes redirectAttributes) { 
		boolean success;
		try {
			RequestForwardEvidenceType requestForwardObj = XDE4AMarshaller.deUsiRequestMarshaller(
					XDE4ACanonicalEvidenceType.BIRTH_CERTIFICATE).read(requestForward);
			responseManager.manageResponse(requestForwardObj);
			success = true;
		} catch (MessageException e) {
			logger.error("There was a problem processing owner USI response");
			success = false;
		}
		ResponseErrorType response = DE4AResponseDocumentHelper.createResponseError(success);
		return DE4AMarshaller.deUsiResponseMarshaller().getAsString(response);
	}
	
	@PostMapping(value = "/viewresponse")
	public String viewresponse(@RequestParam String requestId, HttpServletRequest requesthttp,
			HttpServletResponse httpServletResponse, Model model) {
		User user = new User();
		model.addAttribute("isResponseReady", "true");
		EvaluatorRequest request = evaluatorRequestRepository.findById(requestId).orElse(null);
		EvaluatorRequestData data = new EvaluatorRequestData();
		data.setRequest(request);
		Example<EvaluatorRequestData> example = Example.of(data);
		List<EvaluatorRequestData> registros = evaluatorRequestDataRepository.findAll(example);
		EvaluatorRequestData dataresponse = null;
		if(request.isUsi()) {
			dataresponse = registros.stream()
					.filter(d -> d.getIddata().equals(DE4AConstants.TAG_FORWARD_EVIDENCE_REQUEST)).findFirst().orElse(null);
			if(dataresponse != null) {
				RequestForwardEvidenceType response = XDE4AMarshaller
						.deUsiRequestMarshaller(XDE4ACanonicalEvidenceType.BIRTH_CERTIFICATE).read(dataresponse.getData());
				user.setResponse(XDE4AMarshaller
						.deUsiRequestMarshaller(XDE4ACanonicalEvidenceType.BIRTH_CERTIFICATE).formatted().getAsString(response));			
				response.getDomesticEvidenceList().getDomesticEvidence().stream().forEach(x -> {
					try {						
						user.setNationalResponse((user.getNationalResponse() != null ? user.getNationalResponse() + "\n\r" : "") 
								+ DOMUtils.loadString(DOMUtils.decodeCompressed(x.getEvidenceData())));
					} catch (Exception e) {
						logger.error("Fatality!", e);
					}
				});			
				model.addAttribute("isResponseReady", "true");
			}
		} else {
			dataresponse = registros.stream()
					.filter(d -> d.getIddata().equals(DE4AConstants.TAG_EVIDENCE_RESPONSE)).findFirst().orElse(null);
			if(dataresponse != null) {
				ResponseTransferEvidenceType response = XDE4AMarshaller
						.drImResponseMarshaller(XDE4ACanonicalEvidenceType.BIRTH_CERTIFICATE).read(dataresponse.getData());
				user.setResponse(XDE4AMarshaller
						.drImResponseMarshaller(XDE4ACanonicalEvidenceType.BIRTH_CERTIFICATE).formatted().getAsString(response));
			
				response.getDomesticEvidenceList().getDomesticEvidence().stream().forEach(x -> {
					try {						
						user.setNationalResponse((user.getNationalResponse() != null ? user.getNationalResponse() + "\n\r" : "") 
								+ DOMUtils.loadString(DOMUtils.decodeCompressed(x.getEvidenceData())));
					} catch (Exception e) {
						logger.error("Fatality!", e);
					}
				});			
				model.addAttribute("isResponseReady", "true");
			}
		}
		model.addAttribute("userForm", user);
		model.addAttribute("requestId", requestId);
		return "viewresponse";
	}
}
