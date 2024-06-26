package it.unibo.experiment

import it.unibo.alchemist.model.{Environment, Node, Position}
import it.unibo.alchemist.model.learning.{GlobalExecution, Molecules}
import it.unibo.alchemist.model.molecules.SimpleMolecule
import org.apache.commons.math3.random.RandomGenerator

import scala.jdk.CollectionConverters.IteratorHasAsScala

class StateEvaluationStrategy[T, P <: Position[P]](actualState: Boolean) extends GlobalExecution[T, P] {

  override def execute(environment: Environment[T, P], randomGenerator: RandomGenerator): Unit = {
    nodes(environment).foreach { node =>
      val positions = environment
        .getNeighborhood(node)
        .getNeighbors
        .iterator()
        .asScala
        .toList
        .sortBy(neigh => environment.getDistanceBetweenNodes(node, neigh))
        .take(ExperimentParams.neighbors)
        .map(neigh => toPosition2D(node, neigh, environment))
      val selfPosition = toPosition2D(node, node, environment)
      val myPosition = environment.getPosition(node)
      val state = FlockState(selfPosition, positions)
      val encodedState = FlockState.stateEncoder.encode(state)
      node.setConcentration(new SimpleMolecule("position-x"), myPosition.getCoordinate(0).asInstanceOf[T])
      node.setConcentration(new SimpleMolecule("position-y"), myPosition.getCoordinate(1).asInstanceOf[T])
      storeState(node, state, encodedState)
    }
  }

  private def toPosition2D(center: Node[T], node: Node[T], environment: Environment[T, P]): (Double, Double) = {
    val centerPosition = environment.getPosition(center)
    val position = environment.getPosition(node)
    (
      centerPosition.getCoordinate(0) - position.getCoordinate(0),
      centerPosition.getCoordinate(1) - position.getCoordinate(1)
    )
  }

  private def storeState(node: Node[T], state: FlockState, encodedState: Seq[Double]): Unit = {
    if (actualState) {
      node.setConcentration(new SimpleMolecule(Molecules.actualState), state.asInstanceOf[T])
      node.setConcentration(new SimpleMolecule(Molecules.encodedActualState), encodedState.asInstanceOf[T])
    } else {
      node.setConcentration(new SimpleMolecule(Molecules.nextState), state.asInstanceOf[T])
      node.setConcentration(new SimpleMolecule(Molecules.encodedNextState), encodedState.asInstanceOf[T])
    }
  }

  private def nodes(environment: Environment[T, P]) =
    environment.getNodes.iterator().asScala.toList
}
