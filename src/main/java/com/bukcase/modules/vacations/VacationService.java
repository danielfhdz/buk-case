package com.bukcase.modules.vacations;

import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.Authorizer;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VacationService {

    private final VacationRequestRepository requests;
    private final Authorizer authorizer;

    public VacationService(VacationRequestRepository requests, Authorizer authorizer) {
        this.requests = requests;
        this.authorizer = authorizer;
    }

    @Transactional(readOnly = true)
    public List<VacationRequest> list() {
        return requests.findAll(authorizer.readable(VacationRequest.class));
    }

    @Transactional
    public VacationRequest approve(long id) {
        VacationRequest request = requests.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vacation request " + id + " not found"));
        authorizer.check(AccessLevel.WRITE, request);
        request.approve();
        return request;
    }
}
