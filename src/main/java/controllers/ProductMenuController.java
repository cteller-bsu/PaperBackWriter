package controllers;

import data_structures.Material;
import data_structures.ModularProduct;
import data_structures.Resource;
import data_structures.ResourceType;
import io_pipes.ResourceIOPipe;
import listeners.cost_analysis.AddMaterialListener;
import listeners.cost_analysis.CalculateCostsListener;
import listeners.cost_analysis.SubmitProductListener;
import listeners.general.RemoveRowListener;
import listeners.product_viewer.ViewerFinishedListener;
import listeners.resource_browser.AddResourceRowListener;
import listeners.resource_browser.SaveResourceTableListener;
import managers.ModularProductManager;
import managers.ResourceManager;
import swing_frames.*;
import ui_components.ReadOnlyMaterialPane;
import utilities.CostAnalyzer;
import utilities.Security;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.util.HashMap;
import java.util.Vector;

public class ProductMenuController {

    private ModularProductManager modularProductManager;
    private ResourceManager resourceManager;

    public ProductMenuController(ModularProductManager modularProductManager, ResourceManager resourceManager) {
        this.modularProductManager = modularProductManager;
        this.resourceManager = resourceManager;
    }

    //TODO: DRY violation
    private JFrame showNewWindow(JFrame frame, int closeBehavior) {
        // This is ridiculous but necessary
        frame.setContentPane(frame.getContentPane());

        frame.setDefaultCloseOperation(closeBehavior);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }

    public void mainProductsMenu() {
        resourceManager.fetchResources();
        ProductsMenuFrame menu = new ProductsMenuFrame();
        menu.setBrowseProductsButtonListener(e -> productBrowser());
        menu.setCostAnalysisButtonListener(e -> costAnalysis());
        menu.setResourcesButtonListener(e -> resources());
        showNewWindow(menu, JFrame.DISPOSE_ON_CLOSE)
                .setTitle("PBW - Products");
    }

    public void resources() {
        ResourcesFrame resources = new ResourcesFrame();
        JTable table = resources.getTable();
        table.setModel(makeResourceModel());
        // Make a combo box and populate it with the types of resources.
        JComboBox<ResourceType> resourceTypeDropdown = new JComboBox<>(ResourceType.values());
        // Use that combo box to edit cells in the 'Type' column.
        table.getColumn("Type").setCellEditor(new DefaultCellEditor(resourceTypeDropdown));
        resources.setSaveButtonListener(new SaveResourceTableListener(resources, resourceManager));
        resources.setAddButtonListener(new AddResourceRowListener(table));
        resources.setRemoveButtonListener(new RemoveRowListener(table));
        resources.setCancelButtonListener(e -> resources.dispose());
        showNewWindow(resources, JFrame.DISPOSE_ON_CLOSE)
                .setTitle("PBW Resources");
    }

    public void productBrowser() {
        modularProductManager.fetch();
        ProductBrowserFrame productBrowser = new ProductBrowserFrame();
        productBrowser.setCloseButtonListener(e -> productBrowser.dispose());
        productBrowser.setViewButtonListener(e -> viewProduct(productBrowser));
        productBrowser.setEnableGradingButtonListener(e -> enableGrading(productBrowser));

        DefaultTableModel model = makeProductModel();
        productBrowser.setProductsTableModel(model);

        showNewWindow(productBrowser, JFrame.DISPOSE_ON_CLOSE)
                .setTitle("PBW Product Browser");
    }

    private void enableGrading(ProductBrowserFrame browser) {
        if (new Security().getAuthorization()) {
            browser.enableGradingCheckbox();
        }
    }

    public void viewProduct(ProductBrowserFrame productBrowser) {
        boolean grading = productBrowser.isGrading();
        int selectedRow = productBrowser.getProductsTable().getSelectedRow();
        String id = (String) productBrowser.getProductsTableModel().getValueAt(selectedRow, 0);
        ModularProduct selectedProduct = (ModularProduct) modularProductManager.getFromKey(id);

        ProductViewerFrame viewer = new ProductViewerFrame();
        viewer.setOkButtonListener(new ViewerFinishedListener(viewer, modularProductManager, grading, selectedProduct));
        fillViewerFields(viewer, selectedProduct);

        showNewWindow(viewer, JFrame.DISPOSE_ON_CLOSE)
                .setTitle("PBW - Product Viewer");
    }


    private void fillViewerFields(ProductViewerFrame viewer, ModularProduct selectedProduct) {
        viewer.setIdNumber(selectedProduct.getId());
        viewer.setName(selectedProduct.getName());
        viewer.setProductType(selectedProduct.getType());
        viewer.setDate(selectedProduct.getDate());
        viewer.setProductDescription(selectedProduct.getDescription());
        viewer.setGrade(selectedProduct.getGrade());
        viewer.setTotalCost(selectedProduct.getTotalCost());
        for (Material material : selectedProduct.getMaterials()) {
            ReadOnlyMaterialPane materialPane = new ReadOnlyMaterialPane(material);
            viewer.addReadOnlyMaterialPane(materialPane);
        }
    }

    private DefaultTableModel makeProductModel() {
        // An uneditable table.
        DefaultTableModel model = new DefaultTableModel() {
            public boolean isCellEditable(int x, int y) {
                return false;
            }
        };
        model.addColumn("ID");
        model.addColumn("Name");
        model.addColumn("Grade");
        model.addColumn("Total Cost");
        for (Object value : modularProductManager.getMap().values()) {
            ModularProduct product = (ModularProduct) value;
            model.addRow(new String[]{product.getId(), product.getName(), product.getGrade(), product.getTotalCost()});
        }
        return model;
    }

    public void costAnalysis() {
        CostAnalysisFrame costAnalysis = new CostAnalysisFrame();
        CostAnalyzer analyser = new CostAnalyzer();
        costAnalysis.setIdNumber(ModularProduct.getCurrentID());

        // Set button behavior
        costAnalysis.setCalculateButtonAction(new CalculateCostsListener(costAnalysis, analyser));
        costAnalysis.setCancelButtonAction(e -> costAnalysis.dispose());
        costAnalysis.setSubmitButtonAction(new SubmitProductListener(costAnalysis, modularProductManager));
        costAnalysis.setAddMaterialButtonAction(new AddMaterialListener(costAnalysis, resourceManager.getResourceMap(), new CostAnalyzer()));

        showNewWindow(costAnalysis, JFrame.DISPOSE_ON_CLOSE)
                .setTitle("PBW - Cost Analysis");
    }


    private TableModel makeResourceModel() {
        DefaultTableModel model = new DefaultTableModel();
        HashMap<String, Resource> map = (HashMap<String, Resource>) resourceManager.getResourceMap();

        for (String header : ResourceIOPipe.CSV_FORMAT.getHeader()) {
            model.addColumn(header);
        }
        for (Resource resource : map.values()) {
            Vector<Object> newRow = new Vector<>();
            newRow.add(resource.getType());
            newRow.add(resource.getName());
            newRow.add(resource.getUnitSize());
            newRow.add(String.format("%.2f", resource.getPricePerUnit()));
            model.addRow(newRow);
        }

        return model;
    }
}
