package db.everything;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FileService implements Serializable {
    private static FileService instance;
    private Database database;
    private String tableName = "file";

    static FileService getInstance() {
        if (instance == null) {
            instance = new FileService();
        }
        return instance;
    }

    private FileService() {
        database = Database.getInstance();
    }

    private void createTable() {
        database.createTable(
                tableName,
                "Name VARCHAR(255)",
                "Path VARCHAR(255)",
                "Size INT(6) UNSIGNED",
                "Date TIMESTAMP",
                " PRIMARY KEY (Path, Name)"
        );
    }

    private void dropTable() {
        database.dropTable("file");
    }

    void reindexTable() {
        dropTable();
        createTable();
        List<IFile> allFiles = getFiles();
        insertAll(allFiles);
    }

    void insertAll(List<IFile> files) {
        for (IFile file : files) {
            insert(file);
        }
    }

    void insert(IFile file) {
        database.insert(tableName, new String[]{"Name", "Path", "Size", "Date"}, file.getValuesArray());
    }

    private @Nullable
    String createWhereClaus(@Nullable String text, boolean isMatchCase) {
        if (text == null || text.length() == 0)
            return null;
        else if (!isMatchCase)
            return "lower(Name) regexp '.*" + text.toLowerCase() + ".*'";
        else
            return "Name regexp '.*" + text + ".*'";
    }

    List<IFile> search(@Nullable String text, @Nullable String sort, boolean isMatchCase) {
        List<IFile> list = new ArrayList<>();

        ResultSet rs = database.search(tableName, createWhereClaus(text, isMatchCase), sort);
        try {
            while (rs != null && rs.next()) {
                String name = rs.getString("Name");
                String path = rs.getString("Path");
                long size = rs.getLong("Size");
                String date = rs.getString("Date");

                IFile file = new IFile(name, path, size, date);
                list.add(file);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    private List<IFile> getFiles() {
        String myDocuments = System.getProperty("user.home");

        String dateFormat = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        List<IFile> results = new ArrayList<>();
        try (Stream<Path> files = Files.list(Paths.get(myDocuments))) {
            results = files
                    .map(f -> {
                        try {
                            BasicFileAttributes attribs = Files.readAttributes(f, BasicFileAttributes.class);
                            return new IFile(
                                    f.toFile().getName(),
                                    f.getParent().toFile().getAbsolutePath(),
                                    attribs.size(),
                                    sdf.format(attribs.creationTime().toMillis())
                            );
                        } catch (IOException ignored) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }
}