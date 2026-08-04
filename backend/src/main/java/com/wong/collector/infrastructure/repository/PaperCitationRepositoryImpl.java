package com.wong.collector.infrastructure.repository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.repository.PaperCitationRepository;
import com.wong.collector.infrastructure.persistence.mapper.PaperCitationMapper;
import com.wong.collector.infrastructure.persistence.po.PaperCitationPO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaperCitationRepositoryImpl implements PaperCitationRepository {

    private final PaperCitationMapper mapper;

    @Override
    public boolean insertIgnore(PaperId citingPaperId, PaperId citedPaperId) {
        if (citingPaperId == null || citedPaperId == null) {
            return false;
        }
        if (citingPaperId.getValue().equals(citedPaperId.getValue())) {
            return false;
        }
        PaperCitationPO po = new PaperCitationPO();
        po.setCitingPaperId(citingPaperId.getValue());
        po.setCitedPaperId(citedPaperId.getValue());
        try {
            return mapper.insert(po) > 0;
        } catch (DataIntegrityViolationException ignored) {
            return false;
        }
    }

    @Override
    public long countReferences(PaperId paperId) {
        if (paperId == null) {
            return 0L;
        }
        Long count = mapper.countByCitingPaperId(paperId.getValue());
        return count == null ? 0L : count;
    }
}

