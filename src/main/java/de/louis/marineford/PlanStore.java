package de.louis.marineford;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Laedt/speichert plan.json (Bauteile) und state.json (Anker, To-dos). */
public final class PlanStore {

    public static final class Todo {
        public long id;
        public String text;
        public String assignee = "";
        /** 0 = allgemein, sonst Etappen-Nummer. */
        public int stage;
        public boolean done;
    }

    public static final class State {
        public Anchor anchor;
        public List<Todo> todos = new ArrayList<>();
        public long nextTodoId = 1;
    }

    private final MarinefordPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File planFile;
    private final File stateFile;
    private volatile Plan plan = new Plan();
    private State state = new State();

    public PlanStore(MarinefordPlugin plugin) {
        this.plugin = plugin;
        this.planFile = new File(plugin.getDataFolder(), "plan.json");
        this.stateFile = new File(plugin.getDataFolder(), "state.json");
    }

    public synchronized void load() throws IOException {
        plugin.getDataFolder().mkdirs();
        if (!planFile.exists()) {
            Files.writeString(planFile.toPath(), gson.toJson(DefaultPlan.create()), StandardCharsets.UTF_8);
        }
        try (Reader r = Files.newBufferedReader(planFile.toPath(), StandardCharsets.UTF_8)) {
            Plan p = gson.fromJson(r, Plan.class);
            plan = p == null ? new Plan() : p;
        }
        for (Part p : plan.parts) p.init();
        if (stateFile.exists()) {
            try (Reader r = Files.newBufferedReader(stateFile.toPath(), StandardCharsets.UTF_8)) {
                State s = gson.fromJson(r, State.class);
                if (s != null) state = s;
            }
        }
        if (state.todos == null) state.todos = new ArrayList<>();
    }

    private synchronized void saveState() {
        try {
            Files.writeString(stateFile.toPath(), gson.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("state.json konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public Plan plan() {
        return plan;
    }

    public synchronized Anchor anchor() {
        return state.anchor;
    }

    public synchronized void setAnchor(Anchor a) {
        state.anchor = a;
        saveState();
        for (Part p : plan.parts) p.resetProgress();
    }

    public synchronized List<Todo> todos() {
        return new ArrayList<>(state.todos);
    }

    public synchronized Todo addTodo(String text, String assignee, int stage) {
        Todo t = new Todo();
        t.id = state.nextTodoId++;
        t.text = text;
        t.assignee = assignee == null ? "" : assignee;
        t.stage = stage;
        state.todos.add(t);
        saveState();
        return t;
    }

    public synchronized boolean toggleTodo(long id) {
        for (Todo t : state.todos) {
            if (t.id == id) {
                t.done = !t.done;
                saveState();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean deleteTodo(long id) {
        boolean removed = state.todos.removeIf(t -> t.id == id);
        if (removed) saveState();
        return removed;
    }
}
