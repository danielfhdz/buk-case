package com.bukcase.modules.vacations;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vacations")
class VacationController {

    private final VacationService service;

    VacationController(VacationService service) {
        this.service = service;
    }

    @GetMapping
    List<VacationView> list() {
        return service.list().stream().map(VacationView::of).toList();
    }

    @PostMapping("/{id}/approve")
    VacationView approve(@PathVariable long id) {
        return VacationView.of(service.approve(id));
    }

    record VacationView(long id, String employee, LocalDate start, LocalDate end, String status) {
        static VacationView of(VacationRequest request) {
            return new VacationView(request.getId(), request.getEmployee().getFullName(),
                    request.getStartDate(), request.getEndDate(), request.getStatus());
        }
    }
}
