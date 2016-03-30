package com.azaverukha.testlink.kwsetter;

import com.thoughtworks.selenium.SeleniumException;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.openqa.selenium.By;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;



public class Main extends Application {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    private GridPane root;

    private File file;
    private Label selectedFile;
    TextField addKeywordsInput;
    TextField removeKeywordsInput;
    static WebDriver webDriver;
    static HSSFSheet sheetIdsNotExist;
    static private String currentProject = "";
    static TextArea textArea;
    static ProgressBar progressIndicator;
    static Button applyButton;



    //    ListView<String> listView = new ListView<>();
//
//    ObservableList<String> items = FXCollections.observableArrayList (
//            "Single", "Double", "Suite", "Family App");
//    listView.setItems(items);
   // root.getChildren().add(listView);
    @Override
    public void start(Stage primaryStage) throws Exception{
        Config config = Config.getInstance();
        try{
            config.load();
        }catch (IOException e){
            logger.error("Configuration", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Configuration error");
            alert.setHeaderText("Load configuration");
            alert.setContentText(e.getMessage());

            alert.showAndWait();
        }

        logger.info(String.format("Configuration loaded: Url: %s \n Login: %s \n Password: %s",  config.getTestlinkUrl(), config.getTestLinkLogin(), config.getTestLinkPassword()));

        primaryStage.setTitle("KW Setter");
        primaryStage.setMaximized(false);


        root = new GridPane();
        root.setPadding(new Insets(25,25,25,25));
        root.setHgap(10);
        root.setVgap(10);

        selectedFile = new Label("Selected File:");
        root.add(selectedFile, 0,root.getChildren().size()+1);


        addKeywordsInput = prepareKeywordsList("Add keywords");


        removeKeywordsInput = prepareKeywordsList("Remove keywords");
        progressIndicator = new ProgressBar();

        applyButton = new Button("Apply");

        applyButton.setOnAction(event -> {
            disableActionControls(true);
            if(file == null || !file.exists() ){ selectFile(primaryStage); }


            Thread.UncaughtExceptionHandler h = (th, ex) -> {
                logger.error("Action error", ex);
                Alert alert2 = new Alert(Alert.AlertType.ERROR);
                alert2.setTitle("Action thread error");
                alert2.setHeaderText("Action thread error");
                alert2.setContentText(ex.getMessage());
                disableActionControls(false);
                currentProject = "";
            };

                    Thread thread = new Thread(() -> {
                        logger.info("Update keywords");
                        if (file != null) updateKeywords();
                        disableActionControls(false);
                        currentProject = "";
                    });
                    thread.setUncaughtExceptionHandler(h);
                    thread.start();
        });


        root.add(applyButton, 0,root.getChildren().size()+1);
        textArea = new TextArea();
       // textArea.setMinSize(400,400);
        textArea.setEditable(false);
        //root.add(progressIndicator, 0, root.getChildren().size()+1);
        root.add(textArea, 0, root.getChildren().size()+1);


        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    private void disableActionControls(boolean isDisabled) {
        applyButton.setDisable(isDisabled);
        addKeywordsInput.setDisable(isDisabled);
        removeKeywordsInput.setDisable(isDisabled);
    }

    private void selectFile(Stage primaryStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Test IDs XLS file");
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("MS Office files (*.xls)", "*.xls");
        fileChooser.getExtensionFilters().add(extFilter);
        file = fileChooser.showOpenDialog(primaryStage);
        if(file != null){
            selectedFile.setText("Selected File:" + file.getAbsolutePath());
        }
    }


    public static void main(String[] args) {
        launch(args);
    }


    private TextField prepareKeywordsList(String name){
        Label label = new Label();
        label.setText(name);

        TextField input = new TextField();
        input.setMinWidth(root.getWidth());


        root.add(label, 0,root.getChildren().size()+1);
        root.add(input, 0,root.getChildren().size()+1);
        return input;


    }

    private void openProjectByTestID(String testID) throws Exception {
        if(testID.isEmpty() || testID.length() < 3) return;
        String projectID = testID.substring(0, 3).toLowerCase();
        String project = Config.getInstance().getTestLinkProjects().get(projectID);
        if(project == null){
            String error = "[Open project] project configuration not found for Proj ID: " + projectID + " TestID: " + testID;

            throw new Exception(error);
        }
       if(!currentProject.toLowerCase().equals(project.toLowerCase())){
            openProject(project);
        }
    }

    static private int getLastRowNumber(HSSFSheet sheetIds){
        int lastRow = sheetIds.getLastRowNum();
        textArea.clear();
        for (int i = sheetIds.getFirstRowNum(); i <= lastRow; i++) {
            HSSFRow row = sheetIds.getRow(i);
            String testID = row.getCell(0).getStringCellValue().trim();
            if(testID.isEmpty()) return i==0 ? 0 : i-1;
        }
        return lastRow;
    }



    private void updateKeywords(){

        webDriver = new FirefoxDriver();
        try {

            webDriver.get(Config.getInstance().getTestlinkUrl());
            webDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            login(Config.getInstance().getTestLinkLogin(), Config.getInstance().getTestLinkPassword());


            List<String> kws = getInputKeywords(addKeywordsInput.getText());
            List<String> kwsRemove = getInputKeywords(removeKeywordsInput.getText());


            try (InputStream is = new FileInputStream(file); OutputStream os = new FileOutputStream(file.getParentFile().getAbsolutePath() + "/Temp.xls")) {

                HSSFWorkbook wbIds = new HSSFWorkbook(is);
                HSSFSheet sheetIds = wbIds.getSheetAt(0);

                HSSFWorkbook workbook = new HSSFWorkbook();
                sheetIdsNotExist = workbook.createSheet("Mapped");
                textArea.clear();
                int lastRow = getLastRowNumber(sheetIds);

                for (int i = sheetIds.getFirstRowNum(); i <= lastRow; i++) {

                    HSSFRow row = sheetIds.getRow(i);
                    String testID = row.getCell(0).getStringCellValue().trim();
                    if (testID.isEmpty()) break;
                    String log = String.format("[Start] TC ID: %s Row: %s Total: %s", testID, i + 1, lastRow + 1);

                    textArea.appendText("\n" + log);


                    openProjectByTestID(testID);
                    try {
                        searchTC(testID.trim());
                        setKeywords(kws, kwsRemove);
                        addTestID(testID, null);
                    } catch (Exception e) {
                        logger.error("[Mapping] TestID:" + testID, e);
                        addTestID(testID, e);
                    }
                    log = String.format("[Finish] TC ID: %s Row: %s Total: %s", testID, i + 1, lastRow + 1);

                    textArea.appendText("\n" + log);
                    // progressIndicator.setProgress(i/lastRow);

                }

                workbook.write(os);
            } catch (IOException e) {
                logger.error("Open tests file", e);
            } catch (Exception e) {
                logger.error("Mapping error", e);
            }

        }catch (SeleniumException e){
            logger.error("Selenium error", e);
        } finally{
            webDriver.close();
        }
    }

    private List<String> getInputKeywords(String value) {
        List<String> valuesArray = new ArrayList<>(Arrays.asList(value.split("[#,|]")));
        valuesArray.stream().map(str-> str = str.trim());
        return valuesArray;
    }


    private static void addTestID(String testID, Exception e){
        HSSFRow row = sheetIdsNotExist.createRow(sheetIdsNotExist.getLastRowNum()+1);
        row.createCell(0).setCellValue(testID);
        if(e != null)
            row.createCell(1).setCellValue(e.getMessage());
    }

    private static void openProject(String project) {
        currentProject = project;
        webDriver.switchTo().defaultContent();
        webDriver.switchTo().frame("titlebar");
        Select select = new Select(webDriver.findElement(By.name("testproject")));
        select.selectByVisibleText(project);
        assignKeywords();

    }

    private static void assignKeywords() {
        webDriver.switchTo().defaultContent();
        webDriver.switchTo().frame("mainframe");
        webDriver.findElement(By.linkText("Assign Keywords")).click();
    }

    private static void setKeywords(List<String> keywords, List<String> keywordsRempve) {
        webDriver.switchTo().defaultContent();
        webDriver.switchTo().frame("mainframe").switchTo().frame("workframe");

        Select from = new Select(webDriver.findElement(By.id("from_select_box")));
        Select to = new Select(webDriver.findElement(By.id("to_select_box")));

        keywords.stream().forEach(s -> setKeyword(s, from));
        webDriver.findElement(By.xpath("//div[@class='option_transfer_container']/table/tbody/tr/td[2]/img[2]")).click();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        keywordsRempve.stream().forEach(s -> setKeyword(s, to));
        webDriver.findElement(By.xpath("//div[@class='option_transfer_container']/table/tbody/tr/td[2]/img[3]")).click();


        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        webDriver.findElement(By.name("assigntestcase")).click();
        webDriver.findElement(By.xpath("//p[contains(text(), 'was successfully  updated')]"));
    }

    private static void setKeyword(String keyword, Select select){
        if(keyword.isEmpty()) return;
        if(select.getOptions().stream().anyMatch(s -> (s.getText().equals(keyword)))){
            select.selectByVisibleText(keyword);
        }
    }



    public static void login(String userName, String password){
        webDriver.findElement(By.id("login")).sendKeys(userName);
        webDriver.findElement(By.name("tl_password")).sendKeys(password);
        webDriver.findElement(By.name("login_submit")).click();

    }

    static void searchTC(String tcID){
        webDriver.switchTo().defaultContent();
        webDriver.switchTo().frame("mainframe").switchTo().frame("treeframe");
        WebElement webElement = webDriver.findElement(By.name("filter_tc_id"));
        webElement.clear();
        webElement.sendKeys(tcID);
        //webDriver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
        webDriver.findElement(By.id("doUpdateTree")).click();
        webDriver.findElement(By.id("expand_tree")).click();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        webDriver.findElement(By.partialLinkText(tcID)).click();
    }
}
