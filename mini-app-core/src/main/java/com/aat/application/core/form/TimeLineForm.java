package com.aat.application.core.form;

import com.aat.application.core.data.entity.ZJTEntity;
import com.aat.application.core.data.service.ZJTService;
import com.aat.application.data.entity.ZJTNode;
import com.aat.application.util.GlobalData;
import com.vaadin.componentfactory.timeline.Timeline;
import com.vaadin.componentfactory.timeline.model.Item;
import com.vaadin.componentfactory.timeline.model.ItemGroup;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.io.Serial;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class TimeLineForm<T extends ZJTEntity, S extends ZJTService<T>> extends VerticalLayout {

    @Serial
    private static final long serialVersionUID = -5183438338263448740L;
    protected TextField filterText = new TextField();
    protected Button save;
    protected Button close;
    private Item newItem;
    private final Object entityData;

    private Button addItemButton;
    private final String groupName;
    Timeline timeline;
    protected S service;
    List<String> headers;
    Dictionary<String, String> headerOptions = new Hashtable<>();
    Dictionary<String, Class<?>> headerTypeOptions = new Hashtable<>();
    List<T> itemData = new ArrayList<>();

    public TimeLineForm(Class<T> entityClass, S service, String groupName, Class<? extends ZJTEntity> groupClass) {
        addClassName("demo-app-form");
        this.groupName = groupName;
        this.service = service;
        try {
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            entityData = Class
                    .forName(entityClass.getName(), true, systemClassLoader)
                    .getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        save = new Button("Save");
        close = new Button("Cancel");
        headers = configureHeader(entityClass);
        configureTimeLine(entityClass);
    }

    private List<String> configureHeader(Class<T> entityClass) {
        List<String> fieldNames = new ArrayList<>();
//        headerOptions = new HashMap<>();

        Class<?> currentClass = entityClass;
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                processField(field, fieldNames);
            }
            currentClass = currentClass.getSuperclass();
        }
        return fieldNames;
    }

    private void processField(Field field, List<String> fieldNames) {
        if (field.getAnnotation(jakarta.persistence.Column.class) != null) {
            fieldNames.add(field.getName());
            headerOptions.put(field.getName(), "input");
        }
        if (field.getAnnotation(jakarta.persistence.Enumerated.class) != null) {
            fieldNames.add(field.getName());
            headerTypeOptions.put(field.getName(), field.getType());
            headerOptions.put(field.getName(), "select_enum");
        }
        if (field.getAnnotation(jakarta.persistence.JoinColumn.class) != null) {
            fieldNames.add(field.getName());
            headerOptions.put(field.getName(), "select_class");
            GlobalData.addData(field.getName(), (Class<? extends ZJTEntity>) field.getType());
        }
    }

    private void configureTimeLine(Class<T> entityClass) {
        List<Item> items = getItems();
        List<ItemGroup> itemGroups = getGroupItems((List<ZJTEntity>) GlobalData.listData.get(groupName));
        timeline = new Timeline(items, itemGroups);

        // setting timeline range
        timeline.setTimelineRange(LocalDateTime.of(2023, 1, 1, 0, 0, 0), LocalDateTime.of(2023, 12, 25, 0, 0, 0));

        timeline.setMultiselect(true);
        timeline.setWidthFull();
        boolean bAutoZoom = false;

        // Select Item
        TextField tfSelected = new TextField();

        FormLayout formLayout = addForm(entityClass, bAutoZoom, itemGroups);
//        HorizontalLayout zoomOptionsLayout = getSelectItemAndZoomOptionLayout(timeline, items, tfSelected, bAutoZoom);

//        add(selectRangeLayout, zoomOptionsLayout, timeline);
        add(timeline, formLayout);
//        add(timeline);
    }

    private List<Item> getItems() {

        List<Item> TableData = new ArrayList<>();
        if (filterText != null) itemData = service.findAll(filterText.getValue());
        else itemData = service.findAll(null);

        for (T data :
                itemData) {
            Item item = new Item();

            for (String header : headers) {
                try {
                    Field headerField = null;
                    Class<?> currentDataClass = data.getClass();
                    while (currentDataClass != null) {
                        try {
                            headerField = currentDataClass.getDeclaredField(header);
                            break;
                        } catch (NoSuchFieldException e) {
                            currentDataClass = currentDataClass.getSuperclass();
                        }
                    }
                    if (headerField == null) {
                        throw new RuntimeException("groupId field not found in class hierarchy");
                    }
                    headerField.setAccessible(true);
                    Object dataSel = headerField.get(data);

                    if (header.equals(groupName)) {
                        item.setClassName(((ZJTNode) dataSel).getClassName());
                    }

                    Field itemHeaderField;

                    try {
                        if (headerField.getAnnotation(jakarta.persistence.Id.class) != null) {
                            itemHeaderField = item.getClass().getDeclaredField("id");
                        } else {
                            itemHeaderField = item.getClass().getDeclaredField(header);
                        }
                    } catch (NoSuchFieldException e) {
                        if (header.equals(this.groupName))
                            itemHeaderField = item.getClass().getDeclaredField("group");
                        else
                            continue;
                    }
                    itemHeaderField.setAccessible(true);
                    switch (headerOptions.get(header)) {
                        case "input":
                            if (headerField.getAnnotation(jakarta.persistence.Id.class) != null) {
                                itemHeaderField.set(item, String.valueOf(dataSel));
                            } else {
                                itemHeaderField.set(item, dataSel);
                            }
                            break;
                        case "select_class":
                            if (dataSel == null)
                                dataSel = data.getClass().getDeclaredField(header);
                            Class<?> currentDataSelClass = dataSel.getClass();
                            Field itemIdField = GlobalData.getPrimaryKeyField(currentDataSelClass);

                            if (itemIdField == null) {
                                throw new RuntimeException("groupId field not found in class hierarchy");
                            }
                            itemIdField.setAccessible(true);
                            itemHeaderField.set(item, String.valueOf(itemIdField.get(dataSel)));

//                            String headerName = header.substring(0, 1).toUpperCase()
//                                    + header.substring(1);
//                            GlobalData.addData(headerName);
                            break;
                        default:
                            break;
                    }

                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            TableData.add(item);
        }
        return TableData;
    }

    private List<ItemGroup> getGroupItems(List<ZJTEntity> groupResults) {
        List<ItemGroup> itemGroups = new ArrayList<>();
//        GlobalData.addData(groupName, groupClass);
//        List<ZJTEntity> groupResults = (List<ZJTEntity>) GlobalData.listData.get(groupName);
        for (Object groupResult :
                groupResults) {
            ItemGroup itemGroup = new ItemGroup();
            for (Field field :
                    itemGroup.getClass().getDeclaredFields()) {
                try {
                    Field headerField = null;
                    Class<?> groupResultClass = groupResult.getClass();
                    if (field.getName().equals("groupId"))
                        headerField = GlobalData.getPrimaryKeyField(groupResultClass);
                    else {
                        while (groupResultClass != null) {
                            try {
                                headerField = groupResultClass.getDeclaredField(field.getName());
                                break;
                            } catch (NoSuchFieldException e) {
                                groupResultClass = groupResultClass.getSuperclass();
                            }
                        }
                    }
                    if (headerField == null) {
                        throw new RuntimeException("groupId field not found in class hierarchy");
                    }
                    headerField.setAccessible(true);
                    field.setAccessible(true);
                    field.set(itemGroup, headerField.get(groupResult));
                } catch (IllegalAccessException ignored) {
                }
            }
            itemGroups.add(itemGroup);
        }
        return itemGroups;
    }

    private void setFieldData(String header, Object value) {

        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        try {
            Class<?> currentClass = Class.forName(entityData.getClass().getName(), true, systemClassLoader);
            Field field;
            while (currentClass != null) {
                try {
                    field = currentClass.getDeclaredField(header);
                    field.setAccessible(true);
//                    convertToZJTEntity(value, field.getDeclaringClass());
                    if (value != null)
                        field.set(entityData, GlobalData.convertToZJTEntity(value, value.getClass()));
                    else
                        field.set(entityData, null);
                    break;
//                    try {
//                        Class<?> valueClass = Class.forName(value.getClass().getName(), true, systemClassLoader);
//                        Object castedValue = valueClass.cast(value);
//                        field.set(entityData, castedValue);
//                        break;
//                    } catch (ClassNotFoundException | ClassCastException ignored) {
//                    }
                } catch (NoSuchFieldException | IllegalAccessException ex) {
                    currentClass = currentClass.getSuperclass();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private FormLayout addForm(Class<T> entityClass, boolean bAutoZoom, List<ItemGroup> itemGroups) {
        FormLayout formLayout = new FormLayout();

        VerticalLayout layout = new VerticalLayout();
        TextField title = new TextField("Title");
        layout.add(title);

        DateTimePicker datePicker1 = new DateTimePicker("Item start date: ");
//        datePicker1.setMin(LocalDateTime.of(2023, 1, 10, 0, 0, 0));
//        datePicker1.setMax(LocalDateTime.of(2023, 8, 22, 0, 0, 0));

        DateTimePicker datePicker2 = new DateTimePicker("Item end date: ");
//        datePicker2.setMin(LocalDateTime.of(2023, 1, 10, 0, 0, 0));
//        datePicker2.setMax(LocalDateTime.of(2023, 8, 22, 0, 0, 0));

        datePicker1.addValueChangeListener(e -> {
            if (datePicker1.getValue() == null)
                return;
            setFieldData("start", datePicker1.getValue());
            newItem = createNewItem(datePicker1.getValue(), datePicker2.getValue());
        });
        datePicker2.addValueChangeListener(e -> {
            if (datePicker1.getValue() == null)
                return;
            setFieldData("end", datePicker2.getValue());
            newItem = createNewItem(datePicker1.getValue(), datePicker2.getValue());
        });

        layout.add(datePicker1, datePicker2);

        try {
            for (String header :
                    headers) {
                if (headerOptions.get(header).equals("select_class") && header.equals(this.groupName)) {
                    T itemObj = entityClass.getDeclaredConstructor().newInstance();
                    Field field = itemObj.getClass().getDeclaredField(header);
                    field.setAccessible(true);
                    GlobalData.addData(header, (Class<? extends ZJTEntity>) field.getType());

                    ComboBox<ItemGroup> groupComboBox = new ComboBox<>(header + " Name");
                    List<ItemGroup> groupList = getGroupItems((List<ZJTEntity>) GlobalData.listData.get(header));
                    groupComboBox.setItems(groupList);
                    groupComboBox.setItemLabelGenerator(ItemGroup::getContent);
                    groupComboBox.setRenderer(createRenderer());
                    if (!groupList.isEmpty())
                        groupComboBox.setValue(groupList.get(0));
                    groupComboBox.setAllowCustomValue(true);
                    if (GlobalData.listData.get(header).isEmpty())
                        setFieldData(header, null);
                    else
                        setFieldData(header, GlobalData.listData.get(header).get(0));

                    groupComboBox.addValueChangeListener(e -> {
                        List<?> data;
                        try {
                            data = GlobalData.getDataById(entityData.getClass().getDeclaredField(header).getType(), groupComboBox.getValue().getGroupId());
                        } catch (NoSuchFieldException ex) {
                            throw new RuntimeException(ex);
                        }
                        setFieldData(header, data.get(0));

                        newItem = createNewItem(datePicker1.getValue(), datePicker2.getValue());
                        if (newItem == null) {
                            newItem = new Item();
                        }
                        newItem.setGroup(String.valueOf(groupComboBox.getValue().getGroupId()));
                    });

                    layout.add(groupComboBox);
                }
            }
        } catch (NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException |
                 NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        addItemButton = new Button("Add Item", e -> {
            this.addItemAndSave(newItem, bAutoZoom, entityClass);
            newItem = null;
            datePicker1.clear();
            datePicker2.clear();
        });
        addItemButton.setDisableOnClick(true);
        addItemButton.setEnabled(false);

        formLayout.add(layout, addItemButton);

        return formLayout;
    }

    void addItemAndSave(Item item, boolean bAutoZoom, Class<?> entityClass) {
        if (item.getGroup() == null)
            item.setGroup("0");
        timeline.addItem(item, bAutoZoom);
        CompletableFuture.runAsync(() -> {
            service.save(GlobalData.convertToZJTEntity(entityData, entityClass));
        });
    }


//    private HorizontalLayout getSelectItemAndZoomOptionLayout(Timeline timeline, List<Item> items, TextField textField, boolean bAutoZoom) {
//        VerticalLayout selectLayout = new VerticalLayout();
//        Button setSelectBtn = new Button("Select Item", e -> timeline.setSelectItem(textField.getValue()));
//        selectLayout.add(textField, setSelectBtn);
//
//        HorizontalLayout zoomOptionsLayout = new HorizontalLayout();
//        zoomOptionsLayout.setMargin(true);
//        Button oneDay = new Button("1 day", e -> timeline.setZoomOption(1));
//        Button threeDays = new Button("3 days", e -> timeline.setZoomOption(3));
//        Button fiveDays = new Button("5 days", e -> timeline.setZoomOption(5));
//
//        zoomOptionsLayout.add(oneDay, threeDays, fiveDays, selectLayout);
//
//        timeline.addItemSelectListener(e -> {
//            timeline.onSelectItem(e.getTimeline(), e.getItemId(), bAutoZoom);
//            textField.setValue(e.getItemId());
//        });
//
//        timeline.addGroupItemClickListener(e -> {
//            StringBuilder temp = new StringBuilder();
//            for (Item item : items) {
//                if (Integer.parseInt(item.getGroup()) == Integer.parseInt(e.getGroupId())) {
//                    if (!temp.isEmpty()) temp.append(",").append(item.getId());
//                    else temp.append(item.getId());
//                }
//            }
//            e.getTimeline().onSelectItem(e.getTimeline(), temp.toString(), false);
//        });
//        return zoomOptionsLayout;
//    }

    private Item createNewItem(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            if (start.isBefore(end)) {
                addItemButton.setEnabled(true);

                if (newItem == null)
                    newItem = new Item();
                newItem.setStart(start);
                newItem.setEnd(end);
                return newItem;
            } else {
                Notification.show("End date should be after start date", 5000, Notification.Position.MIDDLE);
                return null;
            }
        } else {
            addItemButton.setEnabled(false);
            return null;
        }
    }

    private Renderer<ItemGroup> createRenderer() {

        return LitRenderer.<ItemGroup>of("<span style= \"font-weight: ${item.width}; font-size: ${item.fontsize}\">${item.content}</span>").withProperty("width", itemGroup -> {
            if (itemGroup.getTreeLevel() == 1) return "bolder";
            else if (itemGroup.getTreeLevel() == 2) return "bold";
            else return "normal";
        }).withProperty("fontsize", itemGroup -> {
            if (itemGroup.getTreeLevel() == 1) return "1rem";
            else if (itemGroup.getTreeLevel() == 2) return "0.9rem";
            else return "0.8rem";
        }).withProperty("content", ItemGroup::getContent);
    }

    private HorizontalLayout getToolbar() {
        filterText.setPlaceholder("Filter by name...");
        filterText.setClearButtonVisible(true);
        filterText.setValueChangeMode(ValueChangeMode.LAZY);
//        filterText.addValueChangeListener(e -> updateList());

        HorizontalLayout toolbar = new HorizontalLayout(filterText);

        toolbar.addClassName("toolbar");

        return toolbar;
    }

    private HorizontalLayout createButtonsLayout() {
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        save.addClickShortcut(Key.ENTER);
        close.addClickShortcut(Key.ESCAPE);

        return new HorizontalLayout(save, close);
    }
}