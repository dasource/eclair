package fr.acinq.eclair.gui

import java.net.ConnectException
import javafx.application.{Application, Platform}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.image.Image
import javafx.scene.text.Text
import javafx.scene.{Group, Parent, Scene}
import javafx.stage.{Modality, Stage, StageStyle, WindowEvent}

import akka.actor.Props
import fr.acinq.eclair.Setup
import fr.acinq.eclair.channel.ChannelEvent
import fr.acinq.eclair.gui.controllers.MainController
import fr.acinq.eclair.gui.stages.SplashStage
import fr.acinq.eclair.router.NetworkEvent
import grizzled.slf4j.Logging

/**
  * Created by PM on 16/08/2016.
  */
class FxApp extends Application with Logging {

  override def start(primaryStage: Stage): Unit = {

    val icon = new Image("/gui/commons/images/eclair02.png", true)
    primaryStage.getIcons().add(icon)
    val splashStage = new SplashStage()
    splashStage.initOwner(primaryStage)
    splashStage.show

    new Thread(new Runnable {
      override def run(): Unit = {

        try {
          val setup = new Setup()
          val handlers = new Handlers(setup)
          val controller = new MainController(handlers, primaryStage, setup)
          val guiUpdater = setup.system.actorOf(Props(classOf[GUIUpdater], primaryStage, controller, setup), "gui-updater")
          setup.system.eventStream.subscribe(guiUpdater, classOf[ChannelEvent])
          setup.system.eventStream.subscribe(guiUpdater, classOf[NetworkEvent])

          import scala.concurrent.ExecutionContext.Implicits.global
          setup.fatalEventFuture onSuccess {
            case e => Platform.runLater(new Runnable {
              override def run(): Unit = {
                val dialog = new Stage()
                dialog.initStyle(StageStyle.UTILITY)
                dialog.setAlwaysOnTop(true)
                dialog.initModality(Modality.APPLICATION_MODAL)
                val scene = new Scene(new Group(new Text(25, 25, s"$e")), 200, 50)
                dialog.setResizable(false)
                dialog.setScene(scene)
                dialog.setTitle("Fatal error")
                dialog.showAndWait()
                Platform.exit()
              }
            })
          }

          Platform.runLater(new Runnable {
            override def run(): Unit = {
              // get fxml/controller
              val mainFXML = new FXMLLoader(getClass.getResource("/gui/main/main.fxml"))
              mainFXML.setController(controller)
              val mainRoot = mainFXML.load[Parent]
              val scene = new Scene(mainRoot)

              primaryStage.setTitle("Eclair")
              primaryStage.widthProperty().addListener(new ChangeListener[Number] {
                override def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number): Unit = controller.handleRefreshGraph
              })
              primaryStage.heightProperty().addListener(new ChangeListener[Number] {
                override def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number): Unit = controller.handleRefreshGraph
              })
              primaryStage.setOnCloseRequest(new EventHandler[WindowEvent] {
                override def handle(event: WindowEvent): Unit = {
                  System.exit(0)
                }
              })
              splashStage.close()
              primaryStage.setScene(scene)
              primaryStage.show()
            }
          })

        } catch {
          case con: ConnectException => {
            logger.error(s"Error when connecting to bitcoin-core: ", con)
            Platform.runLater(new Runnable {
              override def run(): Unit = {
                splashStage.controller.showError("Could not connect to Bitcoin-core.")
              }
            })
          }
          case e: Exception => {
            logger.error(s"Something wrong happened: ", e)
            Platform.runLater(new Runnable {
              override def run(): Unit = {
                splashStage.controller.showError("An error has occured.")
              }
            })
          }
        }
      }

    }).start()

  }

}