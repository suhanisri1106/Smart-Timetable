import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

// ---------------------------- MODEL CLASSES ----------------------------
class Subject {
    String code, name;
    int hoursPerWeek;
    boolean requiresProjector;

    Subject(String code, String name, int hoursPerWeek, boolean requiresProjector) {
        this.code = code;
        this.name = name;
        this.hoursPerWeek = hoursPerWeek;
        this.requiresProjector = requiresProjector;
    }
}

class Faculty {
    String id, name;
    List<String> subjects;

    Faculty(String id, String name, List<String> subjects) {
        this.id = id;
        this.name = name;
        this.subjects = subjects;
    }

    @Override
    public String toString() {
        return name;
    }
}

class Classroom {
    String number;
    int capacity;
    boolean hasProjector;

    Classroom(String number, int capacity, boolean hasProjector) {
        this.number = number;
        this.capacity = capacity;
        this.hasProjector = hasProjector;
    }

    @Override
    public String toString() {
        return number;
    }
}

class StudentBatch {
    String dept;
    int year;
    String section;
    List<Subject> requiredSubjects;

    StudentBatch(String dept, int year, String section, List<Subject> requiredSubjects) {
        this.dept = dept;
        this.year = year;
        this.section = section;
        this.requiredSubjects = requiredSubjects;
    }

    @Override
    public String toString() {
        return dept + " Y" + year + section;
    }
}

class TimeSlot {
    String day, time;

    TimeSlot(String day, String time) {
        this.day = day;
        this.time = time;
    }
}

class Lecture {
    Faculty faculty;
    StudentBatch batch;
    Subject subject;
    Classroom room;
    TimeSlot slot;

    Lecture(Faculty faculty, StudentBatch batch, Subject subject, Classroom room, TimeSlot slot) {
        this.faculty = faculty;
        this.batch = batch;
        this.subject = subject;
        this.room = room;
        this.slot = slot;
    }

    @Override
    public String toString() {
        return String.format("%-5s | %-7s | %-10s | %-20s | %-15s | %-5s",
                slot.day, slot.time, batch, subject.name, faculty.name, room.number);
    }

    public String toCSV() {
        return String.format("%s,%s,%s,%s,%s,%s",
                slot.day, slot.time, batch, subject.name, faculty.name, room.number);
    }
}

// ---------------------------- IMPROVED SCHEDULER ----------------------------
class Scheduler {
    List<Faculty> faculties;
    List<Classroom> rooms;
    List<StudentBatch> batches;
    List<TimeSlot> slots;
    List<Subject> subjects;
    List<Lecture> timetable = new ArrayList<>();

    // Workload tracking
    Map<Faculty, Integer> facultyWeeklyCount = new HashMap<>();
    Map<Faculty, Map<String, Integer>> facultyDailyCount = new HashMap<>();
    Map<StudentBatch, Map<String, Integer>> batchDailyCount = new HashMap<>();

    // Limits
    final int MAX_BATCH_PER_DAY = 5;
    final int MAX_FACULTY_PER_DAY = 4;
    final int MAX_FACULTY_PER_WEEK = 18;

    // Preferred day order for display (extend if you have weekends or custom days)
    final List<String> DAY_ORDER = Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    Scheduler(List<Faculty> faculties, List<Classroom> rooms,
              List<StudentBatch> batches, List<TimeSlot> slots, List<Subject> subjects) {
        this.faculties = faculties;
        this.rooms = rooms;
        this.batches = batches;
        this.slots = slots;
        this.subjects = subjects;

        // Initialize workload counters
        for (Faculty f : faculties) {
            facultyWeeklyCount.put(f, 0);
            facultyDailyCount.put(f, new HashMap<>());
        }
        for (StudentBatch b : batches) {
            batchDailyCount.put(b, new HashMap<>());
        }
    }

    // Clash check (same day & time)
    private boolean isClash(Faculty f, StudentBatch b, Classroom r, TimeSlot t) {
        for (Lecture lec : timetable) {
            if (lec.slot.day.equals(t.day) && lec.slot.time.equals(t.time)) {
                if (lec.faculty.equals(f) || lec.batch.equals(b) || lec.room.equals(r))
                    return true;
            }
        }
        return false;
    }

    // NEW: Workload limits check
    private boolean exceedsWorkload(Faculty f, StudentBatch b, TimeSlot t) {

        // Batch daily limit
        int batchCount = batchDailyCount.get(b).getOrDefault(t.day, 0);
        if (batchCount >= MAX_BATCH_PER_DAY) return true;

        // Faculty weekly limit
        int facWeek = facultyWeeklyCount.get(f);
        if (facWeek >= MAX_FACULTY_PER_WEEK) return true;

        // Faculty daily limit
        int facDay = facultyDailyCount.get(f).getOrDefault(t.day, 0);
        if (facDay >= MAX_FACULTY_PER_DAY) return true;

        return false;
    }

    // Update workload after assigning a lecture
    private void updateWorkload(Faculty f, StudentBatch b, TimeSlot t) {
        // Update batch
        batchDailyCount.get(b).put(
                t.day,
                batchDailyCount.get(b).getOrDefault(t.day, 0) + 1
        );

        // Update faculty weekly count
        facultyWeeklyCount.put(f, facultyWeeklyCount.get(f) + 1);

        // Update faculty daily count
        facultyDailyCount.get(f).put(
                t.day,
                facultyDailyCount.get(f).getOrDefault(t.day, 0) + 1
        );
    }

    void generate() {
        Random rand = new Random();
        Set<String> usedRoomTime = new HashSet<>();

        for (StudentBatch batch : batches) {
            for (Subject sub : batch.requiredSubjects) {

                // Assign any faculty capable of teaching this subject
                Faculty assigned = faculties.stream()
                        .filter(f -> f.subjects.contains(sub.code))
                        .findAny()
                        .orElse(null);
                if (assigned == null) continue;

                int hours = sub.hoursPerWeek;

                for (int i = 0; i < hours; i++) {
                    boolean placed = false;
                    int attempts = 0;

                    while (!placed && attempts < 2000) {

                        TimeSlot slot = slots.get(rand.nextInt(slots.size()));
                        List<Classroom> shuffledRooms = new ArrayList<>(rooms);
                        Collections.shuffle(shuffledRooms);

                        for (Classroom room : shuffledRooms) {
                            String key = slot.day + "-" + slot.time + "-" + room.number;

                            if (usedRoomTime.contains(key)) continue;
                            if (isClash(assigned, batch, room, slot)) continue;
                            if (exceedsWorkload(assigned, batch, slot)) continue;

                            // SUCCESSFUL PLACEMENT
                            timetable.add(new Lecture(assigned, batch, sub, room, slot));
                            usedRoomTime.add(key);
                            updateWorkload(assigned, batch, slot);

                            placed = true;
                            break;
                        }
                        attempts++;
                    }
                }
            }
        }
    }

    // ---------------- Grouped display: Day -> Batch -> Time ----------------
    void printAndExport(String filename) {
        System.out.println("\n==================== SMART TIMETABLE ====================\n");

        // Sort timetable by day index (based on DAY_ORDER) -> batch string -> time
        Comparator<Lecture> lectureComparator = Comparator
                .comparingInt((Lecture l) -> {
                    int idx = DAY_ORDER.indexOf(l.slot.day);
                    return idx >= 0 ? idx : DAY_ORDER.size(); // unknown days go to the end
                })
                .thenComparing(l -> l.batch.toString())
                .thenComparing(l -> l.slot.time);

        timetable.sort(lectureComparator);

        // Group by day -> batch
        LinkedHashMap<String, LinkedHashMap<String, List<Lecture>>> grouped = new LinkedHashMap<>();

        for (Lecture lec : timetable) {
            grouped
                .computeIfAbsent(lec.slot.day, d -> new LinkedHashMap<>())
                .computeIfAbsent(lec.batch.toString(), b -> new ArrayList<>())
                .add(lec);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            // CSV header
            pw.println("Day,Time,Batch,Subject,Faculty,Room");

            // For each day in DAY_ORDER, if present in grouped, print in that order
            for (String day : DAY_ORDER) {
                if (!grouped.containsKey(day)) continue;

                System.out.println("\n==================== " + day.toUpperCase() + " ====================\n");

                LinkedHashMap<String, List<Lecture>> batchesMap = grouped.get(day);

                for (String batchKey : batchesMap.keySet()) {
                    System.out.println(">>> Batch: " + batchKey + "\n");
                    System.out.printf("%-7s | %-20s | %-15s | %-5s%n",
                            "Time", "Subject", "Faculty", "Room");
                    System.out.println("------------------------------------------------------------");

                    List<Lecture> lectures = batchesMap.get(batchKey);
                    // Already sorted by time because of earlier sort, but ensure stable order:
                    lectures.sort(Comparator.comparing(l -> l.slot.time));

                    for (Lecture lec : lectures) {
                        System.out.printf("%-7s | %-20s | %-15s | %-5s%n",
                                lec.slot.time, lec.subject.name, lec.faculty.name, lec.room.number);
                        pw.println(lec.toCSV());
                    }
                    System.out.println();
                }
            }

            // Print any days not in DAY_ORDER (if your CSV has different day names)
            for (String day : grouped.keySet()) {
                if (DAY_ORDER.contains(day)) continue; // already printed
                System.out.println("\n==================== " + day.toUpperCase() + " ====================\n");

                LinkedHashMap<String, List<Lecture>> batchesMap = grouped.get(day);
                for (String batchKey : batchesMap.keySet()) {
                    System.out.println(">>> Batch: " + batchKey + "\n");
                    System.out.printf("%-7s | %-20s | %-15s | %-5s%n",
                            "Time", "Subject", "Faculty", "Room");
                    System.out.println("------------------------------------------------------------");

                    List<Lecture> lectures = batchesMap.get(batchKey);
                    lectures.sort(Comparator.comparing(l -> l.slot.time));

                    for (Lecture lec : lectures) {
                        System.out.printf("%-7s | %-20s | %-15s | %-5s%n",
                                lec.slot.time, lec.subject.name, lec.faculty.name, lec.room.number);
                        pw.println(lec.toCSV());
                    }
                    System.out.println();
                }
            }

            System.out.println("\nTimetable also exported to " + filename);
        } catch (IOException e) {
            System.err.println("Error exporting timetable: " + e.getMessage());
        }
    }
}

// ---------------------------- FILE LOADER ----------------------------
class CSVLoader {
    public static List<Subject> loadSubjects(String filename) throws IOException {
        List<Subject> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                list.add(new Subject(p[0].trim(), p[1].trim(), Integer.parseInt(p[2].trim()), Boolean.parseBoolean(p[3].trim())));
            }
        }
        return list;
    }

    public static List<Faculty> loadFaculties(String filename) throws IOException {
        List<Faculty> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                List<String> subs = Arrays.stream(p[2].split(";")).map(String::trim).collect(Collectors.toList());
                list.add(new Faculty(p[0].trim(), p[1].trim(), subs));
            }
        }
        return list;
    }

    public static List<Classroom> loadClassrooms(String filename) throws IOException {
        List<Classroom> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                list.add(new Classroom(p[0].trim(), Integer.parseInt(p[1].trim()), Boolean.parseBoolean(p[2].trim())));
            }
        }
        return list;
    }

    public static List<TimeSlot> loadTimeSlots(String filename) throws IOException {
        List<TimeSlot> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                list.add(new TimeSlot(p[0].trim(), p[1].trim()));
            }
        }
        return list;
    }

    public static List<StudentBatch> loadBatches(String filename, List<Subject> subjects) throws IOException {
        List<StudentBatch> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                String dept = p[0].trim();
                int year = Integer.parseInt(p[1].trim());
                String section = p[2].trim();
                List<Subject> reqSubs = Arrays.stream(p[3].split(";"))
                        .map(code -> subjects.stream()
                                .filter(s -> s.code.equals(code.trim()))
                                .findFirst()
                                .orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                list.add(new StudentBatch(dept, year, section, reqSubs));
            }
        }
        return list;
    }
}

// ---------------------------- MAIN ----------------------------
public class SmartTimetableScheduler {
    public static void main(String[] args) {
        try {
            // Load all data from CSV files (place these files in working directory)
            List<Subject> subjects = CSVLoader.loadSubjects("subjects.csv");
            List<Faculty> faculties = CSVLoader.loadFaculties("faculty.csv");
            List<Classroom> rooms = CSVLoader.loadClassrooms("classrooms.csv");
            List<TimeSlot> slots = CSVLoader.loadTimeSlots("timeslots.csv");
            List<StudentBatch> batches = CSVLoader.loadBatches("batches.csv", subjects);

            // Generate and output timetable
            Scheduler scheduler = new Scheduler(faculties, rooms, batches, slots, subjects);
            scheduler.generate();
            scheduler.printAndExport("timetable.csv");

        } catch (IOException e) {
           System.err.println("Error exporting timetable: " + e.getMessage());
        }
    }
}
