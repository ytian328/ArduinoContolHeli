package ArduinoControlHeli;

import org.opencv.core.Core;

import java.net.URL;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;

public class ArduinoControlHeliMain extends Application
{
	/**
	 * 
	 * This application tracks rc helicopter in the camera video stream and
	 * try to send control code from Arduino Uno board to keep the helicopter staying in the 
                    * center of the camera screen.
                    * 
                    * The JavaFX framework is adopted from Luigi De Russis' tennis tracking project
	 * 
	 */
	@Override
	public void start(Stage primaryStage)
	{
		try
		{
			// load the FXML resource

			BorderPane root = (BorderPane) FXMLLoader.load(getClass().getResource("ObjectDetection.fxml"));
			// set a whitesmoke background
			root.setStyle("-fx-background-color: whitesmoke;");
			// create and style a scene
			Scene scene = new Scene(root, 800, 600);
			//scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			// create the stage with the given title and the previously created
			// scene
			primaryStage.setTitle("Object Recognition");
			primaryStage.setScene(scene);
			// show the GUI
			primaryStage.show();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		// load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		launch(args);
	}
}