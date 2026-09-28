package com.bukcase.modules.complaints;

import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.Authorizer;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintService {

    private final ComplaintRepository complaints;
    private final Authorizer authorizer;

    public ComplaintService(ComplaintRepository complaints, Authorizer authorizer) {
        this.complaints = complaints;
        this.authorizer = authorizer;
    }

    @Transactional(readOnly = true)
    public List<Complaint> list() {
        return complaints.findAll(authorizer.readable(Complaint.class));
    }

    @Transactional(readOnly = true)
    public Complaint get(long id) {
        Complaint complaint = complaints.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Complaint " + id + " not found"));
        authorizer.check(AccessLevel.READ, complaint);
        return complaint;
    }
}
