package com.bukcase.modules.complaints;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/complaints")
class ComplaintController {

    private final ComplaintService service;

    ComplaintController(ComplaintService service) {
        this.service = service;
    }

    @GetMapping
    List<ComplaintView> list() {
        return service.list().stream().map(ComplaintView::of).toList();
    }

    @GetMapping("/{id}")
    ComplaintView get(@PathVariable long id) {
        return ComplaintView.of(service.get(id));
    }

    record ComplaintView(long id, String type, String description) {
        static ComplaintView of(Complaint complaint) {
            return new ComplaintView(complaint.getId(), complaint.getType().getName(), complaint.getDescription());
        }
    }
}
