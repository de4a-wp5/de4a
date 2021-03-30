package eu.de4a.evaluator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import eu.de4a.evaluator.model.EvaluatorRequestData;
import eu.de4a.evaluator.model.RequestDataPK;

@Repository
public interface EvaluatorRequestDataRepository extends JpaRepository<EvaluatorRequestData, RequestDataPK> {

}
