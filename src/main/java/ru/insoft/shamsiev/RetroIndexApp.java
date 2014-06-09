package ru.insoft.shamsiev;

/**
 * Created by artur.shamsiev on 09.06.2014.
 */
import com.google.common.base.Strings;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class RetroIndexApp extends Application {

    public static final String CONFIG_FILENAME = "retroconversion_config.properties";
    public static final String XML_FILE_NAME = "index.xml";
    private Properties properties;
    private String[] nameArray;
    private String inputDirectory;
    private String outputDirectory;
    private boolean confirmed = false;
    private String courtNumber;
    private String typeCode;
    private String[] currentCorts;
    private String storedRegion;
    private String pageNumber;

    Label regionNameLabel = new Label();
    Label courtNameLabel = new Label();

    RegionTableRow selectedRegion;
    CourtTableRow selectedCourt;

    private static Map<String, Map<String, List<String>>> courtRegionsMap = new LinkedHashMap();
    private List<String> currentCourtView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start(final Stage firstStage) throws Exception {

        parseXML();

        // Первое окно с выбором директорий
        firstStage.setTitle("Программа индексации компонента «Ретроконверсии» КП ЭХСД");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(30, 10, 10, 10));

        Label inputLabel = new Label();
        inputLabel.setText("Искать в");
        final TextField inputField = new TextField();
        inputField.setText(inputDirectory);
        inputField.setMinSize(400, 1);
        Button inputSelect = new Button();
        inputSelect.setText("Обзор");

        Label outputLabel = new Label();
        outputLabel.setText("Сохранять в");
        final TextField outputField = new TextField();
        outputField.setText(outputDirectory);
        outputField.setMinSize(400, 1);
        Button outputSelect = new Button();
        outputSelect.setText("Обзор");

        Button directoryNext = new Button();
        directoryNext.setText("Далее");
        directoryNext.requestFocus();

        grid.add(inputLabel, 0, 0);
        grid.add(inputField, 1, 0);
        grid.add(inputSelect, 2, 0);
        grid.add(outputLabel, 0, 1);
        grid.add(outputField, 1, 1);
        grid.add(outputSelect, 2, 1);
        grid.add(directoryNext, 2, 2);

        inputSelect.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("Выберите папку");

                File inputDir = directoryChooser.showDialog(null);

                if (inputDir != null) {
                    inputField.setText(inputDir.getAbsolutePath());
                    nameArray = inputDir.list();
                }
            }
        });

        outputSelect.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("Выберите папку для сохранения");

                File outputDir = directoryChooser.showDialog(null);

                if (outputDir != null) {
                    outputField.setText(outputDir.getAbsolutePath());

                }

            }
        });

        directoryNext.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                inputDirectory = inputField.getText();
                outputDirectory = outputField.getText();

                if (Strings.isNullOrEmpty(outputDirectory) || Strings.isNullOrEmpty(inputDirectory)) {
                    final Stage dialog = new Stage();
                    dialog.initModality(Modality.WINDOW_MODAL);
                    dialog.initOwner(((Node)actionEvent.getSource()).getScene().getWindow());
                    VBox vBox = new VBox();
                    vBox.setAlignment(Pos.CENTER);
                    vBox.setSpacing(10);
                    Button btn = new Button("OK");
                    btn.setMinWidth(50);
                    btn.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent actionEvent) {
                            dialog.close();
                        }
                    });
                    vBox.getChildren().add(new Text(23, 23, "Не выбрана папка!"));
                    vBox.getChildren().add(btn);
                    Scene scene = new Scene(vBox, 150, 60);
                    dialog.setScene(scene);
                    dialog.setResizable(false);
                    dialog.show();
                } else {
                    writeDirectoryConfig();
                    firstStage.close();
                    createSecondWindow().show();
                }


            }
        });

        firstStage.setScene(new Scene(grid, 600, 300));
        firstStage.setResizable(false);
        firstStage.show();

        directoryNext.requestFocus();

    }

    private Stage createSecondWindow() {
        typeCode = "1";

        final String[] REGIONS_ARRAY = courtRegionsMap.keySet().toArray(new String[courtRegionsMap.keySet().size()]);
        final String[] TYPES_ARRAY = new String[] {"Производство по делам об административных правонарушениях", "Гражданское производство", "Уголовное производство", "Производство по материалам", "Общее делопроизводство", "Дела по личному составу"};
        final String[] TYPE_CODES_ARRAY = new String[] {"1", "2", "3", "6", "144", "145"};

        final Stage stage = new Stage();
        stage.setTitle("Программа индексации компонента «Ретроконверсии» КП ЭХСД");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setVgap(10);
        grid.setHgap(10);

        Label regionLabel = new Label("Регион");
        Label courtNumberLabel = new Label("Номер суда");
        Label typeLabel = new Label("Тип производства");
        Label caseNumberLabel = new Label("Производственный номер дела");
        Label booksAmountLabel = new Label("Количество томов дела");
        Label booksNumberLabel = new Label("Номер тома дела");
        Label documentsNumberLabel = new Label("Количество отсканированных документов");
        Label pageNumberLabel = new Label("Страница");

        final TextField courtNumberField = new TextField();
        courtNumberField.setText(courtNumber);
        addTextLimiter(courtNumberField, 8);

        ChoiceBox typesChoiceBox = new ChoiceBox(FXCollections.observableArrayList(TYPES_ARRAY));
        typesChoiceBox.getSelectionModel().selectFirst();


        System.err.println(courtRegionsMap.keySet().toString());

        Map<String, List<String>> firstRegionMap;
        if (Strings.isNullOrEmpty(storedRegion)) {
            firstRegionMap = courtRegionsMap.get(REGIONS_ARRAY[0]);
        } else {
            firstRegionMap = courtRegionsMap.get(storedRegion);
        }
        // test
        currentCorts = firstRegionMap.keySet().toArray(new String[firstRegionMap.keySet().size()]);
        currentCourtView = new ArrayList();
        for (int i = 0; i < currentCorts.length; i++) {
            String currentCort = currentCorts[i];
            List<String> list = firstRegionMap.get(currentCort);
            currentCourtView.add(list.get(0) + " - " + list.get(1));
        }


        // применяем сохраненные значения
        regionNameLabel.setText(Strings.isNullOrEmpty(storedRegion) ? REGIONS_ARRAY[0] : storedRegion);
        int index = Arrays.asList(currentCorts).indexOf(courtNumber);
        courtNameLabel.setText(Strings.isNullOrEmpty(courtNumber) ? currentCourtView.get(0) : currentCourtView.get(index));

        final TextField caseNumberField = new TextField();
        addTextLimiter(caseNumberField, 20);

        final TextField booksAmountField = new TextField();
        addTextLimiter(booksAmountField, 3);

        final TextField booksNumberField = new TextField();
        addTextLimiter(booksNumberField, 3);

        final TextField documentsNumberField = new TextField();
        addTextLimiter(documentsNumberField, 3);

        final TextField pageNumberField = new TextField();
        addTextLimiter(pageNumberField, 3);

        final Button indexButton = new Button();
        indexButton.setText("Индексировать");
        HBox box = new HBox(10);
        box.setAlignment(Pos.BOTTOM_RIGHT);
        box.getChildren().add(indexButton);

        final Button selectRegionButton = new Button("Выбрать регион");
        final Button selectCourtButton = new Button("Выбрать суд");

        grid.add(regionLabel, 0, 0);
        grid.add(regionNameLabel, 1, 0);
        grid.add(selectRegionButton, 2, 0);

        grid.add(courtNumberLabel, 0, 1);
        grid.add(courtNameLabel, 1, 1);
        grid.add(selectCourtButton, 2, 1);

//        grid.add(courtNumberField, 1, 7);

        grid.add(typeLabel, 0, 2);
        grid.add(typesChoiceBox, 1, 2);

        grid.add(caseNumberLabel, 0 , 3);
        grid.add(caseNumberField, 1 , 3);

        grid.add(booksAmountLabel, 0, 4);
        grid.add(booksAmountField, 1, 4);

        grid.add(booksNumberLabel, 0, 5);
        grid.add(booksNumberField, 1, 5);

        grid.add(documentsNumberLabel, 0, 6);
        grid.add(documentsNumberField, 1, 6);

//        grid.add(pageNumberLabel, 0, 7);
//        grid.add(pageNumberField, 1, 7);
        grid.add(box, 1, 8);

        typesChoiceBox.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                typeCode = TYPE_CODES_ARRAY[number2.intValue()];
            }
        });

        indexButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                indexFiles(actionEvent, courtNumberField, caseNumberField, booksAmountField, booksNumberField, documentsNumberField, pageNumberField, stage, indexButton);
            }
        });

        selectRegionButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                createRegionTable().show();
            }
        });

        selectCourtButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                createCourtsTable().show();
            }
        });

        stage.setScene(new Scene(grid, 800, 350));
        stage.setResizable(false);
        return stage;
    }

    private void indexFiles(ActionEvent actionEvent, TextField courtNumberField, TextField caseNumberField, TextField booksAmountField, TextField booksNumberField, TextField documentsNumberField, TextField pageNumberField, Stage stage, Button indexButton) {
        // поиск и перемещение файлов
        final String placeholderSubdirectory = "%s-%s-%s";
        final String placeholderRegex = "D-%s-%s-%s-%s-\\d+-\\d+\\.pdf";
        Path source = Paths.get(inputDirectory);
        Path target = Paths.get(outputDirectory);

        String caseNumber = caseNumberField.getText().replaceAll("/", "_").replaceAll("-", "_");
        String booksAmount = booksAmountField.getText();
        String bookNumber = booksNumberField.getText();
        String documentsNumber = documentsNumberField.getText();
        String pageNumber = pageNumberField.getText();

        String outputSubdirectory = String.format(placeholderSubdirectory, getCourtId(), typeCode, caseNumber);
        Path subdirectory = target.resolve(outputSubdirectory);
        Path indexFile = subdirectory.resolve(XML_FILE_NAME);
        if (Files.exists(indexFile)) {
            final Stage dialog = new Stage();
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(((Node)actionEvent.getSource()).getScene().getWindow());
            VBox vBox = new VBox();
            vBox.setAlignment(Pos.CENTER);
            vBox.setSpacing(10);
            Button btn = new Button("OK");
            btn.setMinWidth(50);
            btn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    dialog.close();
                }
            });
            vBox.getChildren().add(new Text(23, 23, "Индексный файл уже существует.\nИндексация невозможна!"));
            vBox.getChildren().add(btn);
            Scene scene = new Scene(vBox, 220, 60);
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.show();
            return;
        }

        String searchRegex = String.format(placeholderRegex, getCourtId(), typeCode, caseNumber, bookNumber);

        Pattern pattern = Pattern.compile(searchRegex);

        // создается поддиректория
        try {
            if (!Files.exists(subdirectory)) {
                Files.createDirectory(subdirectory);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> fileNameList = new ArrayList();

        // проверка на соответствие количества документов
        if (!confirmed) {
            int docCount = 0;
            for (File file : source.toFile().listFiles()) {
                String fileName = file.getName();
                if (pattern.matcher(fileName).matches()) {
                    docCount++;
                }
            }
            if (docCount != Integer.parseInt(documentsNumber)) {
                formDocumentAmountConfirmation(actionEvent, courtNumberField, caseNumberField, booksAmountField, booksNumberField, documentsNumberField, pageNumberField, stage, indexButton).show();
                return;
            }
        }

        for (File file : source.toFile().listFiles()) {
            String fileName = file.getName();
            if (pattern.matcher(file.getName()).matches()) {
                fileNameList.add(fileName);
                Path path = subdirectory.resolve(fileName);
                try {
                    if (Files.exists(path))
                        Files.delete(path);
                    Files.move(source.resolve(fileName), path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        writeCourtNumberConfig();

        // преобразование serverId в courtId
        String courtId = getCourtId();

        // формирование XML
        String resultXML = buildXML(courtId, typeCode, caseNumberField.getText(), booksAmount, bookNumber, documentsNumber, fileNameList);

        OutputStreamWriter os;
        File f = new File(subdirectory.toString() +  "/" + XML_FILE_NAME);
        try {
            os = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            os.write(resultXML);
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(((Node)actionEvent.getSource()).getScene().getWindow());
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setAlignment(Pos.CENTER);
        Button btn = new Button("OK");
        btn.setMinWidth(50);
        btn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                dialog.close();
            }
        });
        vBox.getChildren().add(new Text(23, 23, "Индексация успешно произведена!"));
        vBox.getChildren().add(btn);
        Scene scene = new Scene(vBox, 220, 60);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.show();

        confirmed = false;
    }

    private String getCourtId() {
        return courtRegionsMap.get(storedRegion).get(courtNumber).get(0);
    }

    private void writeDirectoryConfig() {
        File file = new File(System.getProperty("user.home"), CONFIG_FILENAME);
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            properties.setProperty("inputDirectory", inputDirectory);
            properties.setProperty("outputDirectory", outputDirectory);

            properties.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (output != null) try {
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeCourtNumberConfig() {
        File file = new File(System.getProperty("user.home"), CONFIG_FILENAME);
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            properties.setProperty("courtNumber", courtNumber);

            properties.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (output != null) try {
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeRegionConfig() {
        File file = new File(System.getProperty("user.home"), CONFIG_FILENAME);
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            properties.setProperty("region", storedRegion);

            properties.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (output != null) try {
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // загружаем сохраненную конфигурацию
    public RetroIndexApp() {
        Properties prop = new Properties();

        File file = new File(System.getProperty("user.home"), CONFIG_FILENAME);
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            prop.load(input);

            courtNumber = prop.getProperty("courtNumber");
            inputDirectory = prop.getProperty("inputDirectory");
            outputDirectory = prop.getProperty("outputDirectory");
            storedRegion = prop.getProperty("region");
        }
        catch (FileNotFoundException e) {

        }
        catch (IOException e) {
            e.printStackTrace();
        } finally {
            properties = prop;
            if (input != null) try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void addTextLimiter(final TextField tf, final int maxLength) {
        tf.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> ov, final String oldValue, final String newValue) {
                if (tf.getText().length() > maxLength) {
                    String s = tf.getText().substring(0, maxLength);
                    tf.setText(s);
                }
            }
        });
    }

    private String buildXML(String courtCodeNumber,
                            String typeCode,
                            String originalCaseNumber,
                            String casesAmount,
                            String bookNumber,
                            String filesNumber,
                            List<String> filesList) {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = null;
        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        Document document = docBuilder.newDocument();
        document.setXmlStandalone(true);

        Element root = document.createElement("ScannedFileList");
        document.appendChild(root);

        Element courtCode = document.createElement("CourtCode");
        courtCode.appendChild(document.createTextNode(courtCodeNumber));
        root.appendChild(courtCode);

        Element caseType = document.createElement("CaseType");
        caseType.appendChild(document.createTextNode(typeCode));
        root.appendChild(caseType);

        Element caseNumber = document.createElement("CaseNumber");
        caseNumber.appendChild(document.createTextNode(originalCaseNumber));
        root.appendChild(caseNumber);

        Element caseVolumes = document.createElement("CaseVolumes");
        caseVolumes.appendChild(document.createTextNode(casesAmount));
        root.appendChild(caseVolumes);

        Element volume = document.createElement("Volume");
        volume.appendChild(document.createTextNode(bookNumber));
        root.appendChild(volume);

        Element scannedFiles = document.createElement("ScannedFiles");
        scannedFiles.appendChild(document.createTextNode(filesNumber));
        root.appendChild(scannedFiles);

        Element files = document.createElement("Files");
        for (String s : filesList) {
            Element file = document.createElement("File");
            file.appendChild(document.createTextNode(s));
            files.appendChild(file);
        }
        root.appendChild(files);


        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute("indent-number", 4);
        Transformer transformer = null;
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        }
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "windows-1251");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource domSource = new DOMSource(document);
        StringWriter sw = new StringWriter();
        StreamResult result = null;
        result = new StreamResult(sw);
        try {
            transformer.transform(domSource, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }

        return sw.toString();
    }

    private Stage formDocumentAmountConfirmation(ActionEvent actionEvent, final TextField courtNumberField, final TextField caseNumberField, final TextField booksAmountField, final TextField booksNumberField, final TextField documentsNumberField, final TextField pageNumberField, final Stage owner, final Button indexButton) {
        final Stage stage = new Stage();

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.BOTTOM_RIGHT);
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        Text text = new Text("Количество найденных документов\n" +
                "не соответствует введенному количеству\n" +
                "отсканированных документов.\nПродолжить?");
        grid.add(text, 0, 0);

        Button yesButton = new Button();
        yesButton.setText("Да");
        yesButton.setMinWidth(75);
        grid.add(yesButton, 0, 1);

        Button noButton = new Button();
        noButton.setText("Нет");
        noButton.setMinWidth(75);
        grid.add(noButton, 1, 1);

        noButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                stage.close();
            }
        });

        yesButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                confirmed = true;
                stage.close();
                indexFiles(actionEvent, courtNumberField, caseNumberField, booksAmountField, booksNumberField, documentsNumberField, pageNumberField, stage, indexButton);
            }
        });


        Scene scene = new Scene(grid, 350, 150);

        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setScene(scene);

        return stage;
    }

    private void parseXML() throws ParserConfigurationException, IOException, SAXException, URISyntaxException {
        InputStream is = RetroIndexApp.class.getResourceAsStream("/serverList.xml");
        System.err.println("HELLO NOT!!");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(is);

        NodeList regionsList = doc.getElementsByTagName("region");
        for (int i = 0; i < regionsList.getLength(); i++) {
            Element region = ((Element) regionsList.item(i));

            Map<String, List<String>> servers = new LinkedHashMap();
            NodeList serverNodes = region.getElementsByTagName("server");

            for (int j = 0; j < serverNodes.getLength(); j++) {
                Element server = ((Element) serverNodes.item(j));
                servers.put(server.getAttribute("serverId"), Arrays.asList(server.getAttribute("courtId"), server.getAttribute("serverName")));

            }
            courtRegionsMap.put(region.getAttribute("regionName"), servers);
        }
    }
    // comment

    private Stage createRegionTable() {
        final String[] REGIONS_ARRAY = courtRegionsMap.keySet().toArray(new String[courtRegionsMap.keySet().size()]);

        final Stage stageRegions = new Stage();

        Scene scene = new Scene(new Group());
        stageRegions.setTitle("Выберете регион");
        stageRegions.setWidth(400);

        final Label label = new Label("Регионы");
        label.setFont(new Font("Arial", 20));

        final TableView table = new TableView();
        table.setEditable(false);

        TableColumn regionName = new TableColumn("Регион");
        table.getColumns().add(regionName);
        regionName.setCellValueFactory(new PropertyValueFactory<RegionTableRow, String>("regionName"));

        final ObservableList<RegionTableRow> regionData = FXCollections.observableArrayList();
        for (int i = 0; i < REGIONS_ARRAY.length; i++) {
            String s = REGIONS_ARRAY[i];
            regionData.add(new RegionTableRow(s));
        }
        table.setItems(regionData);
        table.getSelectionModel().select(Arrays.asList(REGIONS_ARRAY).indexOf(regionNameLabel.getText()));

        Button selectRegionButton = new Button("OK");
        selectRegionButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                selectedRegion = ((RegionTableRow) table.getSelectionModel().getSelectedItem());
                regionNameLabel.setText(selectedRegion.getRegionName());
                storedRegion = selectedRegion.getRegionName();

                Set<String> regionCourtsSet = courtRegionsMap.get(selectedRegion.getRegionName()).keySet();
                currentCorts = regionCourtsSet.toArray(new String[regionCourtsSet.size()]);
                currentCourtView.clear();
                for (int i = 0; i < currentCorts.length; i++) {
                    String currentCort = currentCorts[i];
                    List<String> list = courtRegionsMap.get(selectedRegion.getRegionName()).get(currentCort);
                    currentCourtView.add(list.get(0) + " - " + list.get(1));
                }
                courtNameLabel.setText(currentCourtView.get(0));
                courtNumber = currentCorts[0];
                writeRegionConfig();
                writeCourtNumberConfig();
                stageRegions.close();
            }
        });

        final VBox vbox = new VBox();
        vbox.setSpacing(5);
        vbox.setPadding(new Insets(10, 0, 0, 10));
        vbox.getChildren().addAll(label, table, selectRegionButton);
        ((Group) scene.getRoot()).getChildren().addAll(vbox);

        stageRegions.setScene(scene);

        return stageRegions;
    }

    public static class RegionTableRow {
        private final SimpleStringProperty regionName;

        private RegionTableRow(String name) {
            regionName = new SimpleStringProperty(name);
        }

        public String getRegionName() {
            return regionName.get();
        }

        public void setRegionName(String regionName) {
            this.regionName.set(regionName);
        }
    }

    private Stage createCourtsTable() {
        final Map<String, List<String>> courtsMap = courtRegionsMap.get(regionNameLabel.getText());

        final Stage stageCourts = new Stage();

        Scene scene = new Scene(new Group());
        stageCourts.setTitle("Выберите суд");
        stageCourts.setWidth(570);
        stageCourts.setHeight(530);

        final Label label = new Label("Суды");
        label.setFont(new Font("Arial", 20));

        final TableView table = new TableView();
        table.setEditable(false);
        table.setMinWidth(450);

        TableColumn regionName = new TableColumn("Суд");
        table.getColumns().add(regionName);
        regionName.setCellValueFactory(new PropertyValueFactory<RegionTableRow, String>("courtLongName"));

        final ObservableList<CourtTableRow> courtData = FXCollections.observableArrayList();
        for (String courtNum : currentCorts) {
            int index = Arrays.asList(currentCorts).indexOf(courtNum);
            courtData.add(new CourtTableRow(courtNum, currentCourtView.get(index)));
        }

        table.setItems(courtData);
        table.getSelectionModel().select(currentCourtView.indexOf(courtNameLabel.getText()));

        Button selectCourtButton = new Button("OK");
        selectCourtButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                selectedCourt = ((CourtTableRow) table.getSelectionModel().getSelectedItem());
                courtNameLabel.setText(selectedCourt.getCourtLongName());
                courtNumber = selectedCourt.getCourtNumber();
                writeCourtNumberConfig();
                stageCourts.close();
            }
        });

        // фильтр
        final HBox filterBox = new HBox();
        filterBox.setSpacing(5);
        final TextField filterField = new TextField();
        final Button filterButton = new Button("Применить фильтр");
        filterBox.getChildren().addAll(filterField, filterButton);

        filterButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                String text = filterField.getText();
                ObservableList<CourtTableRow> filteredList = filterCourts(text);
                table.setItems(filteredList);
            }
        });

        final VBox vbox = new VBox();
        vbox.setSpacing(5);
        vbox.setPadding(new Insets(10, 0, 0, 10));
        vbox.getChildren().addAll(label, filterBox, table, selectCourtButton);
        ((Group) scene.getRoot()).getChildren().addAll(vbox);

        stageCourts.setScene(scene);

        return stageCourts;
    }

    private ObservableList<CourtTableRow> filterCourts(String str) {
        str = str.toLowerCase();
        ObservableList<CourtTableRow> courtData = FXCollections.observableArrayList();

        for (String courtNum : currentCorts) {
            int index = Arrays.asList(currentCorts).indexOf(courtNum);
            String courtLongName = currentCourtView.get(index);
            String lowerCaseName = courtLongName.toLowerCase();
            if (lowerCaseName.contains(str)) {
                courtData.add(new CourtTableRow(courtNum, courtLongName));
            }
        }
        return courtData;
    }

    public static class CourtTableRow {
        private final SimpleStringProperty courtNumber;
        private final SimpleStringProperty courtLongName;

        private CourtTableRow(String number, String longName) {
            courtNumber = new SimpleStringProperty(number);
            courtLongName = new SimpleStringProperty(longName);
        }

        public String getCourtNumber() {
            return courtNumber.get();
        }

        public SimpleStringProperty courtNumberProperty() {
            return courtNumber;
        }

        public void setCourtNumber(String courtNumber) {
            this.courtNumber.set(courtNumber);
        }

        public String getCourtLongName() {
            return courtLongName.get();
        }

        public SimpleStringProperty courtLongNameProperty() {
            return courtLongName;
        }

        public void setCourtLongName(String courtLongName) {
            this.courtLongName.set(courtLongName);
        }
    }


}

