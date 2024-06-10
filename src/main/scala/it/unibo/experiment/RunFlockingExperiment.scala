package it.unibo.experiment

import it.unibo.alchemist.model.learning.BatchLearning

object RunFlockingExperiment extends App {

  private val strategies = List(
    new StateEvaluationStrategy[Any, Nothing](true),
    new ActionChoiceStrategy[Any, Nothing],
    new CollectiveActionExecutionStrategy[Any, Nothing],
    new StateEvaluationStrategy[Any, Nothing](false),
    new RewardEvaluationStrategy[Any, Nothing],
    new ExperienceCollectionStrategy[Any, Nothing]
  )

  private val learner = BatchLearning(
    strategies,
    "src/main/yaml/simulation.yml",
    List("seed"),
    10,
    64,
    "seed"
  )

  learner.startLearning()

}