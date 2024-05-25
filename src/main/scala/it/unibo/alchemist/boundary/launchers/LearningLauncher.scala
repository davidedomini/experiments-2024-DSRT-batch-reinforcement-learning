package it.unibo.alchemist.boundary.launchers

import com.google.common.collect.Lists
import it.unibo.alchemist.boundary.{Launcher, Loader, Variable}
import it.unibo.alchemist.core.Simulation
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.util.BugReporting
import it.unibo.interop.PythonModules.pythonUtils
import org.slf4j.{Logger, LoggerFactory}
import scala.jdk.CollectionConverters._
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.{Failure, Success}

class LearningLauncher (
                         val batch: java.util.ArrayList[String],
                         val autoStart: Boolean,
                         val showProgress: Boolean,
                         val globalRounds: Int
                       ) extends Launcher {

  private val parallelism: Int = Runtime.getRuntime.availableProcessors()
  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private val errorQueue = new ConcurrentLinkedQueue[Throwable]()
  private val executor = Executors.newFixedThreadPool(parallelism)
  private implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(executor)

  override def launch(loader: Loader): Unit = {
    val instances = loader.getVariables
    val prod = cartesianProduct(instances, batch)

    Range.inclusive(1, globalRounds).foreach { iter =>
      logger.info(s"Starting Global Round: $iter")
      val futures = prod.zipWithIndex.map {
        case (instance, index) =>
          val sim = loader.getWith[Any, Nothing](instance.asJava)
          runSimulationAsync(sim, index, instance)
      }
      Await.ready(Future.sequence(futures), Duration.Inf)
      // TODO - learning
    }

    executor.shutdown()
    executor.awaitTermination(Long.MaxValue, TimeUnit.DAYS)
    // TODO - throws errors in errorQueue
  }

  private def cartesianProduct(
    variables: java.util.Map[String, Variable[_]],
    variablesNames: java.util.List[String]
  ): List[mutable.Map[String, Serializable]] = {
    val l = variablesNames.stream().map(
      variable => {
        val values = variables.get(variable)
        values.stream().map(e => variable -> e).toList
      }).toList
    Lists.cartesianProduct(l)
      .stream()
      .map(e => { mutable.Map.from(e.iterator().asScala.toList) })
      .iterator().asScala.toList
      .asInstanceOf[List[mutable.Map[String, Serializable]]]
  }

  private def runSimulationAsync(
    simulation: Simulation[Any, Nothing],
    index: Int,
    instance: mutable.Map[String, Serializable]
  )(implicit executionContext: ExecutionContext): Future[Unit] = {
    val future = Future {
      neuralNetworkInjection(simulation)
      simulation.play()
      simulation.run()
      simulation.getError.ifPresent { error => throw error}
      logger.info("Simulation with {} completed successfully", instance)
    }
    future.onComplete {
      case Success(_) =>
        // TODO - collect experience from agents
        logger.info("Simulation {} of {} completed", index + 1, instance.size)
      case Failure(exception) =>
        logger.error(s"Failure for simulation with $instance", exception)
        errorQueue.add(exception)
        executor.shutdownNow()
    }
    future
  }

  private def neuralNetworkInjection(simulation: Simulation[Any, Nothing]): Unit = {
    val model = pythonUtils.load_neural_network()
    simulation
      .getEnvironment
      .getNodes
      .iterator()
      .asScala.toList
      .foreach { node =>
         node.setConcentration(new SimpleMolecule("Model"), model)
      }
  }

}