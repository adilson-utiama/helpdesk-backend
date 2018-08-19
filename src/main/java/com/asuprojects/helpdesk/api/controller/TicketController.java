package com.asuprojects.helpdesk.api.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asuprojects.helpdesk.api.dto.Summary;
import com.asuprojects.helpdesk.api.entity.ChangeStatus;
import com.asuprojects.helpdesk.api.entity.Ticket;
import com.asuprojects.helpdesk.api.entity.User;
import com.asuprojects.helpdesk.api.enums.ProfileEnum;
import com.asuprojects.helpdesk.api.enums.StatusEnum;
import com.asuprojects.helpdesk.api.response.Response;
import com.asuprojects.helpdesk.api.security.jwt.JwtTokenUtil;
import com.asuprojects.helpdesk.api.service.TicketService;
import com.asuprojects.helpdesk.api.service.UserService;

@RestController
@RequestMapping("/api/ticket")
@CrossOrigin(value = "*")
public class TicketController {

	@Autowired
	private TicketService ticketService;
	
	@Autowired
	private JwtTokenUtil jwtTokenUtil;
	
	@Autowired
	private UserService userService;
	
	@PostMapping()
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public ResponseEntity<Response<Ticket>> create(HttpServletRequest request, @RequestBody Ticket ticket, BindingResult result){
		Response<Ticket> response = new Response<Ticket>();
		try {
			validateCreateTicket(ticket, result);
			if(result.hasErrors()) {
				result.getAllErrors().forEach(error -> response.getErrors().add(error.getDefaultMessage()));
				return ResponseEntity.badRequest().body(response);
			}
			ticket.setStatus(StatusEnum.getStatus("New"));
			ticket.setUser(userFromRequest(request));
			ticket.setDate(new Date());
			ticket.setNumber(generateNumber());
			Ticket ticketPersisted = ticketService.createOrUpdate(ticket);
			response.setData(ticketPersisted);
		} catch (Exception e) {
			response.getErrors().add(e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
		
		return ResponseEntity.ok(response);
	}
	
	@PutMapping
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public ResponseEntity<Response<Ticket>> update(HttpServletRequest request, @RequestBody Ticket ticket, BindingResult result){
		Response<Ticket> response = new Response<Ticket>();
		try {
			validateUpdateUser(ticket, result);
			if(result.hasErrors()) {
				result.getAllErrors().forEach(error -> response.getErrors().add(error.getDefaultMessage()));
				return ResponseEntity.badRequest().body(response);
			}
			Optional<Ticket> optional = ticketService.findById(ticket.getId());
			if(optional.isPresent()){
				Ticket ticketCurrent = optional.get();
				ticket.setStatus(ticketCurrent.getStatus());
				ticket.setDate(ticketCurrent.getDate());
				ticket.setNumber(ticket.getNumber());
				ticket.setUser(ticketCurrent.getUser());
				if(ticket.getAssignedUser() != null) {
					ticket.setAssignedUser(ticketCurrent.getAssignedUser());
				}
				Ticket ticketPersisted = ticketService.createOrUpdate(ticket);
				response.setData(ticketPersisted);
			}
		} catch (Exception e) {
			response.getErrors().add(e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
		
		return ResponseEntity.ok(response);	
	}
	
	@GetMapping(value = "{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER','TECHNICIAN')")
	public ResponseEntity<Response<Ticket>> findById(@PathVariable("id") String id){
		Response<Ticket> response = new Response<Ticket>();
		Optional<Ticket> optional = ticketService.findById(id);
		if(optional.isPresent()) {
			Ticket ticket = optional.get();
			List<ChangeStatus> changes = new ArrayList<>();
			Iterable<ChangeStatus> changesCurrent = ticketService.listChangeStatus(ticket.getId());
			for (Iterator<ChangeStatus> iterator = changesCurrent.iterator(); iterator.hasNext();) {
				ChangeStatus changeStatus = (ChangeStatus) iterator.next();
				changeStatus.setTicket(null);
				changes.add(changeStatus);
			}
			ticket.setChanges(changes);
			response.setData(ticket);
		} else {
			response.getErrors().add("Register not found for ID: " + id);
			return ResponseEntity.badRequest().body(response);
		}
		return ResponseEntity.ok(response);
	}
	
	@DeleteMapping(value = "{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public ResponseEntity<Response<String>> delete(@PathVariable("id") String id){
		Response<String> response = new Response<String>();
		Optional<Ticket> optional = ticketService.findById(id);
		if(optional.isPresent()) {
			ticketService.delete(id);
			return ResponseEntity.ok(new Response<String>());
		} else {
			response.getErrors().add("Register not found for Id: " + id);
			return ResponseEntity.badRequest().body(response);
		} 
			
	}
	
	@GetMapping(value = "{page}/{count}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'TECHNICIAN')")
	public ResponseEntity<Response<Page<Ticket>>> findAll(HttpServletRequest request, @PathVariable("page") int page,
			@PathVariable("count") int count){
		Response<Page<Ticket>> response = new Response<Page<Ticket>>();
		Page<Ticket> tickets = null;
		User userCurrent = userFromRequest(request);
		if(userCurrent.getProfile().equals(ProfileEnum.ROLE_TECHNICIAN)) {
			tickets = ticketService.listTicket(page, count);
		} else if (userCurrent.getProfile().equals(ProfileEnum.ROLE_CUSTOMER)) {
			tickets = ticketService.findByCurrentUser(page, count, userCurrent.getId());
		}
		response.setData(tickets);
		return ResponseEntity.ok(response);
		
	}
	
	@GetMapping(value = "{page}/{count}/{number}/{title}/{status}/{priority}/{assigned}")
	@PreAuthorize("hasAnyRole('CUSTOMER','TECHNICIAN')")
	public ResponseEntity<Response<Page<Ticket>>> findByParams(HttpServletRequest request,
			@PathVariable("page") int page, @PathVariable("count") int count,
			@PathVariable("number") int number, @PathVariable("title") String title,
			@PathVariable("status") String status, @PathVariable("priority") String priority,
			@PathVariable("assigned") boolean assigned){
		title = title.equals("uninformed") ? "" : title;
		status = status.equals("uninformed") ? "" : status;
		priority = priority.equals("uninformed") ? "" : priority;
		Response<Page<Ticket>> response = new Response<Page<Ticket>>(); 
		Page<Ticket> tickets = null;
		if(number > 0) {
			tickets = ticketService.findByNumber(page, count, number);
		} else {
			User currentUser = userFromRequest(request);
			if(currentUser.getProfile().equals(ProfileEnum.ROLE_TECHNICIAN)) {
				if(assigned) {
					tickets = ticketService.findByParameterAndAssignedUser(page, count, title, status, priority, currentUser.getId());
				} else {
					tickets = ticketService.findByParameters(page, count, title, status, priority);
				}
			} else if (currentUser.getProfile().equals(ProfileEnum.ROLE_CUSTOMER)) {
				tickets = ticketService.findByParametersAndCurrentUser(page, count, title, status, priority, currentUser.getId());
			}
		}
		response.setData(tickets);
		return ResponseEntity.ok(response);
		
	}
	
	@PutMapping(value = "{id}/{status}")
	@PreAuthorize("hasAnyRole('CUSTOMER','TECHNICIAN')")
	public ResponseEntity<Response<Ticket>> changeStatus(HttpServletRequest request,
			@PathVariable("id") String id, @PathVariable("status") String status,
			@RequestBody Ticket ticket, BindingResult result){
		Response<Ticket> response = new Response<Ticket>();
		try {
			validateChangeStatus(id, status, result);
			if(result.hasErrors()) {
				result.getAllErrors().forEach(error -> response.getErrors().add(error.getDefaultMessage()));
				return ResponseEntity.badRequest().body(response);
			}
			Optional<Ticket> optional = ticketService.findById(id);
			if(optional.isPresent()) {
				Ticket ticketCurrent = optional.get();
				ticketCurrent.setStatus(StatusEnum.getStatus(status));
				if(status.equals("Assigned")) {
					ticketCurrent.setAssignedUser(userFromRequest(request));
				}
				Ticket ticketPersisted = ticketService.createOrUpdate(ticketCurrent);
				ChangeStatus changeStatus = new ChangeStatus();
				changeStatus.setUserChange(userFromRequest(request));
				changeStatus.setDateChangeStatus(new Date());
				changeStatus.setStatus(StatusEnum.getStatus(status));
				changeStatus.setTicket(ticketPersisted);
				ticketService.createChangeStatus(changeStatus);
				response.setData(ticketPersisted);
			}
		} catch (Exception e) {
			response.getErrors().add(e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/summary")
	public ResponseEntity<Response<Summary>> findSummary(){
		Response<Summary> response = new Response<Summary>();
		Summary summary = new Summary();
		int amountNew = 0;
		int amountApproved = 0;
		int amountDisapproved = 0;
		int amountResolved = 0;
		int amountAssigned = 0;
		int amountClosed = 0;
		
		Iterable<Ticket> tickets = ticketService.findAll();
		for (Iterator<Ticket> iterator = tickets.iterator(); iterator.hasNext();) {
			Ticket ticket = iterator.next();
			if(ticket.getStatus().equals(StatusEnum.New)) {
				amountNew++;
			}
			if(ticket.getStatus().equals(StatusEnum.Approved)) {
				amountApproved++;
			}
			if(ticket.getStatus().equals(StatusEnum.Disapproved)) {
				amountDisapproved++;
			}
			if(ticket.getStatus().equals(StatusEnum.Resolved)) {
				amountResolved++;
			}
			if(ticket.getStatus().equals(StatusEnum.Assigned)) {
				amountAssigned++;
			}
			if(ticket.getStatus().equals(StatusEnum.Closed)) {
				amountClosed++;
			}
			
		}
		summary.setAmountNew(amountNew);
		summary.setAmountApproved(amountApproved);
		summary.setAmountDisapproved(amountDisapproved);
		summary.setAmountResolved(amountResolved);
		summary.setAmountAssigned(amountAssigned);
		summary.setAmountClosed(amountClosed);
		response.setData(summary);
		return ResponseEntity.ok(response);
	}

	private void validateChangeStatus(String id, String status, BindingResult result) {
		if(id == null || id.equals("")) {
			result.addError(new ObjectError("Ticket", "Id has no information"));
			return;
		}
		if(status == null || status.equals("")) {
			result.addError(new ObjectError("Ticket", "Status has no information"));
		}
	}

	private void validateUpdateUser(Ticket ticket, BindingResult result) {
		if(ticket.getId() == null) {
			result.addError(new ObjectError("Ticket", "No Id information"));
			return;
		}
		if(ticket.getTitle() == null) {
			result.addError(new ObjectError("Ticket","No title information"));
			return;
		}
	}

	private Integer generateNumber() {
		Random random = new Random();
		return random.nextInt(9999);
	}


	public User userFromRequest(HttpServletRequest request) {
		String token = request.getHeader("Authorization");
		String email = jwtTokenUtil.getUserNameFromToken(token);
		User user = userService.findByEmail(email);
		return user;
	}


	public void validateCreateTicket(Ticket ticket, BindingResult result) {
		if(ticket.getTitle() == null) {
			result.addError(new ObjectError("Ticket","No title information"));
			return;
		}
		
	}
}
