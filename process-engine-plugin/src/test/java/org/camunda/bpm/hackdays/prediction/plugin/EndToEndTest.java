package org.camunda.bpm.hackdays.prediction.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.hackdays.prediction.CmmnPredictionService;
import org.camunda.bpm.hackdays.prediction.model.PredictionModelParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class EndToEndTest {

  public ProcessEngineRule engineRule = new ProcessEngineRule("camunda.cfg.xml");
  
  public PredictionTestRule predictionRule = new PredictionTestRule(engineRule);
  
  @Rule
  public RuleChain rule = RuleChain.outerRule(engineRule).around(predictionRule);
  
  protected CaseService caseService;
  protected RepositoryService repositoryService;
  protected CmmnPredictionService predictionService;
  
  protected String deploymentId;
  
  @Before
  public void setUp() {
    caseService = engineRule.getCaseService();
    repositoryService = engineRule.getRepositoryService();
    predictionService = predictionRule.getPredictionService();
  }
  
  @After
  public void tearDown() {
    if (deploymentId != null) {
      repositoryService.deleteDeployment(deploymentId, true);
    }
  }
  
  @Test
  public void shouldEstimateModelVariable() {
    // given
    deploymentId = repositoryService.createDeployment()
      .addClasspathResource("twoTasksCase.cmmn")
      .addInputStream("twoTasksCase.cmmn.json", EndToEndTest.class.getClassLoader().getResourceAsStream("model.json"))
      .deploy()
      .getId();
    
    CaseDefinition caseDefinition = repositoryService.createCaseDefinitionQuery().singleResult();
    
    CaseInstance caseInstance = caseService
      .withCaseDefinition(caseDefinition.getId())
      .setVariable("intVal", 75)
      .create();

    caseService.withCaseExecution(caseInstance.getId()).complete();
    caseService.withCaseExecution(caseInstance.getId()).close();
    
    // when
    caseService
      .withCaseDefinition(caseDefinition.getId())
      .create();
    
    Map<String, Double> estimation = predictionService.estimate(caseDefinition.getId(), "bar", new HashMap<String, Integer>(), new HashMap<String, Object>());
    assertThat(estimation.size()).isEqualTo(2);
    assertThat(estimation.get("barCat2")).isGreaterThan(estimation.get("barCat1"));
  }
  
  @Test
  public void shouldEstimateActivityVariable() {
    // given
    deploymentId = repositoryService.createDeployment()
      .addClasspathResource("twoTasksCase.cmmn")
      .addInputStream("twoTasksCase.cmmn.json", EndToEndTest.class.getClassLoader().getResourceAsStream("model-with-binary-var.json"))
      .deploy()
      .getId();
    
    CaseDefinition caseDefinition = repositoryService.createCaseDefinitionQuery().singleResult();
    
    CaseInstance caseInstance = caseService
      .withCaseDefinition(caseDefinition.getId())
      .setVariable("intVal", 75)
      .create();
    
    CaseExecution caseExecution = caseService.createCaseExecutionQuery().activityId("PlanItem_1").singleResult();
    caseService.withCaseExecution(caseExecution.getId()).manualStart();
    caseService.withCaseExecution(caseExecution.getId()).complete();

    caseService.withCaseExecution(caseInstance.getId()).complete();
    caseService.withCaseExecution(caseInstance.getId()).close();
    
    // when
    
    Map<String, Double> estimation = predictionService.estimate(
        caseDefinition.getId(), 
        "PlanItem_1", 
        new HashMap<String, Integer>(), 
        new HashMap<String, Object>());

    Double probabilityPerformActivity = estimation.get(PredictionModelParser.VARIABLE_TYPE_BINARY_TRUE);
    
    Map<String, Double> estimationGivenValue = predictionService.estimate(
        caseDefinition.getId(), 
        "PlanItem_1", 
        new HashMap<String, Integer>(), 
        Collections.<String, Object>singletonMap("intVal", 60));
    Double probabilityPerformActivityGivenValue = estimationGivenValue.get(PredictionModelParser.VARIABLE_TYPE_BINARY_TRUE);
    
    assertThat(probabilityPerformActivityGivenValue).isGreaterThan(probabilityPerformActivity);
  }
}
