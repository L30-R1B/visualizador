package com;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import io.github.cdimascio.dotenv.Dotenv;

public class Visualizar {
    private static String sqlServerUrl, sqlServerUser, sqlServerPassword;
    private static String mysqlUrl, mysqlUser, mysqlPassword;
    private static List<String[]> tablePairs;
    private static Random random = new Random();
    
    public static void main(String[] args) {
        carregarCredenciais();
        SwingUtilities.invokeLater(Visualizar::criarGUI);
    }

    private static void carregarCredenciais() {
        Dotenv dotenv = Dotenv.load();
        sqlServerUrl = dotenv.get("SQLSERVER_URL");
        sqlServerUser = dotenv.get("SQLSERVER_USER");
        sqlServerPassword = dotenv.get("SQLSERVER_PASSWORD");
        mysqlUrl = dotenv.get("MYSQL_URL");
        mysqlUser = dotenv.get("MYSQL_USER");
        mysqlPassword = dotenv.get("MYSQL_PASSWORD");

        String tabelas = dotenv.get("PARES_TABELAS");
        tablePairs = Arrays.stream(tabelas.split(";"))
                           .map(pair -> pair.split(","))
                           .toList();
    }

    private static void criarGUI() {
        JFrame frame = new JFrame("Visualização de Tabelas - SQL Server (Protheus) e MySQL (Cloud SQL)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        frame.add(tabbedPane);

        for (String[] pair : tablePairs) {
            String sqlServerTable = pair[0];
            String mysqlTable = pair[1];

            JPanel panelTabela = new JPanel(new GridLayout(2, 1));

            JPanel panelSQLServer = criarPainelTabela("SQL Server (Protheus) - " + sqlServerTable);
            panelTabela.add(panelSQLServer);

            JPanel panelMySQL = criarPainelTabela("MySQL (Cloud SQL) - " + mysqlTable);
            panelTabela.add(panelMySQL);

            tabbedPane.addTab(sqlServerTable + " / " + mysqlTable, panelTabela);

            Timer timer = new Timer(5000, e -> {
                if (tabbedPane.getSelectedComponent() == panelTabela) {
                    atualizarTabela(panelSQLServer, sqlServerUrl, sqlServerUser, sqlServerPassword, sqlServerTable);
                    atualizarTabela(panelMySQL, mysqlUrl, mysqlUser, mysqlPassword, mysqlTable);
                }
            });
            timer.start();
        }

        JPanel panelControle = new JPanel();
        JButton botaoInserirSQLServer = new JButton("Inserir Manualmente no SQL Server");
        botaoInserirSQLServer.addActionListener(e -> mostrarDialogoInserirDadosSQLServer());
        
        panelControle.add(botaoInserirSQLServer);
        frame.add(panelControle, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private static JPanel criarPainelTabela(String titulo) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(titulo, JLabel.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(label, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void atualizarTabela(JPanel panel, String url, String user, String password, String tableName) {
        new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                DefaultTableModel model = new DefaultTableModel();
                for (int i = 1; i <= columnCount; i++) {
                    model.addColumn(metaData.getColumnName(i));
                }

                while (rs.next()) {
                    Object[] row = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    model.addRow(row);
                }

                SwingUtilities.invokeLater(() -> {
                    JScrollPane scrollPane = (JScrollPane) panel.getComponent(1);
                    JTable table = (JTable) scrollPane.getViewport().getView();
                    table.setModel(model);

                    if (panel.getComponentCount() > 2) {
                        panel.remove(2);
                        panel.revalidate();
                        panel.repaint();
                    }
                });
            } catch (SQLException ex) {
                if (ex.getMessage().contains("Invalid object name") || ex.getMessage().contains("Table") && ex.getMessage().contains("doesn't exist")) {
                    SwingUtilities.invokeLater(() -> {
                        JLabel labelErro = new JLabel("Tabela não existe", JLabel.CENTER);
                        labelErro.setFont(new Font("Arial", Font.BOLD, 36));
                        labelErro.setForeground(java.awt.Color.RED);

                        if (panel.getComponentCount() > 2) {
                            panel.remove(2);
                        }

                        panel.add(labelErro, BorderLayout.SOUTH);
                        panel.revalidate();
                        panel.repaint();
                    });
                } else {
                    ex.printStackTrace();
                    System.err.println("Erro ao conectar ao banco de dados: " + ex.getMessage());
                }
            }
        }).start();
    }

    private static void mostrarDialogoInserirDadosSQLServer() {
        try (Connection conn = DriverManager.getConnection(sqlServerUrl, sqlServerUser, sqlServerPassword)) {
            String[] tabelas = obterTabelas(conn);
            
            if (tabelas.length == 0) {
                JOptionPane.showMessageDialog(null, "Nenhuma tabela encontrada no banco de dados.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            String tabelaSelecionada = (String) JOptionPane.showInputDialog(
                null, 
                "Selecione a tabela para inserção:", 
                "Selecionar Tabela", 
                JOptionPane.QUESTION_MESSAGE, 
                null, 
                tabelas, 
                tabelas[0]);
            
            if (tabelaSelecionada == null) return;
            
            ColumnInfo[] colunas = obterColunasComTipo(conn, tabelaSelecionada);
            
            if (colunas.length == 0) {
                JOptionPane.showMessageDialog(null, "Nenhuma coluna encontrada na tabela.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            JPanel panel = new JPanel(new GridLayout(colunas.length, 3));
            JTextField[] campos = new JTextField[colunas.length];
            
            for (int i = 0; i < colunas.length; i++) {
                panel.add(new JLabel(colunas[i].name + ":"));
                
                campos[i] = new JTextField(20);
                panel.add(campos[i]);
                
                JButton botaoRandom = new JButton("Random");
                final int index = i;
                final ColumnInfo coluna = colunas[i];
                botaoRandom.addActionListener(e -> {
                    campos[index].setText(gerarValorAleatorio(coluna));
                });
                panel.add(botaoRandom);
            }
            
            int resultado = JOptionPane.showConfirmDialog(
                null, 
                panel, 
                "Inserir dados na tabela " + tabelaSelecionada, 
                JOptionPane.OK_CANCEL_OPTION, 
                JOptionPane.PLAIN_MESSAGE);
            
            if (resultado == JOptionPane.OK_OPTION) {
                inserirDados(conn, tabelaSelecionada, colunas, campos);
            }
            
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro ao conectar ao banco de dados: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String[] obterTabelas(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            java.util.List<String> tabelas = new java.util.ArrayList<>();
            while (rs.next()) {
                tabelas.add(rs.getString("TABLE_NAME"));
            }
            return tabelas.toArray(new String[0]);
        }
    }

    private static ColumnInfo[] obterColunasComTipo(Connection conn, String tabela) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tabela, null)) {
            java.util.List<ColumnInfo> colunas = new java.util.ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int type = rs.getInt("DATA_TYPE");
                int length = rs.getInt("COLUMN_SIZE");
                colunas.add(new ColumnInfo(name, type, length));
            }
            return colunas.toArray(new ColumnInfo[0]);
        }
    }

    private static void inserirDados(Connection conn, String tabela, ColumnInfo[] colunas, JTextField[] campos) {
        try {
            StringBuilder sql = new StringBuilder("INSERT INTO " + tabela + " (");
            for (int i = 0; i < colunas.length; i++) {
                sql.append(colunas[i].name);
                if (i < colunas.length - 1) sql.append(", ");
            }
            sql.append(") VALUES (");
            for (int i = 0; i < colunas.length; i++) {
                sql.append("?");
                if (i < colunas.length - 1) sql.append(", ");
            }
            sql.append(")");
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < campos.length; i++) {
                    String value = campos[i].getText();
                    
                    if (value.isEmpty()) {
                        pstmt.setNull(i + 1, colunas[i].type);
                    } else {
                        try {
                            switch (colunas[i].type) {
                                case Types.INTEGER:
                                case Types.SMALLINT:
                                case Types.TINYINT:
                                    pstmt.setInt(i + 1, Integer.parseInt(value));
                                    break;
                                case Types.BIGINT:
                                    pstmt.setLong(i + 1, Long.parseLong(value));
                                    break;
                                case Types.DECIMAL:
                                case Types.NUMERIC:
                                case Types.FLOAT:
                                case Types.DOUBLE:
                                    // Substituir vírgula por ponto para formato decimal americano
                                    String decimalValue = value.replace(',', '.');
                                    pstmt.setDouble(i + 1, Double.parseDouble(decimalValue));
                                    break;
                                case Types.BIT:
                                case Types.BOOLEAN:
                                    pstmt.setBoolean(i + 1, Boolean.parseBoolean(value) || "1".equals(value));
                                    break;
                                default:
                                    if (colunas[i].length > 0 && value.length() > colunas[i].length) {
                                        value = value.substring(0, colunas[i].length);
                                        JOptionPane.showMessageDialog(null, 
                                            "Valor truncado para a coluna " + colunas[i].name + 
                                            " para " + colunas[i].length + " caracteres",
                                            "Aviso", JOptionPane.WARNING_MESSAGE);
                                    }
                                    pstmt.setString(i + 1, value);
                            }
                        } catch (NumberFormatException e) {
                            JOptionPane.showMessageDialog(null, 
                                "Valor inválido para a coluna " + colunas[i].name + 
                                ". Esperado: " + getTypeName(colunas[i].type) + 
                                "\nValor fornecido: " + value +
                                "\nPara decimais, use ponto (.) como separador",
                                "Erro de Formato", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    }
                }
                
                int linhasAfetadas = pstmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Dados inseridos com sucesso! Linhas afetadas: " + linhasAfetadas);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro ao inserir dados: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String gerarValorAleatorio(ColumnInfo coluna) {
        switch (coluna.type) {
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return String.valueOf(random.nextInt(100));
                
            case Types.BIGINT:
                return String.valueOf(random.nextLong() % 10000L);
                
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.DOUBLE:
                return String.format("%.2f", random.nextDouble() * 100);
                
            case Types.BIT:
            case Types.BOOLEAN:
                return random.nextBoolean() ? "1" : "0";
                
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
                String texto = "Texto_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
                if (coluna.length > 0) {
                    texto = texto.substring(0, Math.min(texto.length(), coluna.length));
                }
                return texto;
                
            case Types.DATE:
                return String.format("%04d-%02d-%02d", 
                    2000 + random.nextInt(24), 
                    1 + random.nextInt(12), 
                    1 + random.nextInt(28));
                    
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
                return String.format("%02d:%02d:%02d", 
                    random.nextInt(24), 
                    random.nextInt(60), 
                    random.nextInt(60));
                    
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return String.format("%04d-%02d-%02d %02d:%02d:%02d", 
                    2000 + random.nextInt(24), 
                    1 + random.nextInt(12), 
                    1 + random.nextInt(28),
                    random.nextInt(24), 
                    random.nextInt(60), 
                    random.nextInt(60));
                    
            default:
                return "Valor_" + UUID.randomUUID().toString().substring(0, 4);
        }
    }

    private static String getTypeName(int type) {
        switch (type) {
            case Types.INTEGER: return "INTEIRO";
            case Types.SMALLINT: return "PEQUENO INTEIRO";
            case Types.TINYINT: return "TINY INT";
            case Types.BIGINT: return "LONGO INTEIRO";
            case Types.DECIMAL: return "DECIMAL";
            case Types.NUMERIC: return "NUMÉRICO";
            case Types.FLOAT: return "FLOAT";
            case Types.DOUBLE: return "DOUBLE";
            case Types.BIT: return "BIT/BOOLEANO";
            case Types.BOOLEAN: return "BOOLEANO";
            case Types.CHAR: return "CARACTERE";
            case Types.VARCHAR: return "TEXTO";
            case Types.LONGVARCHAR: return "TEXTO LONGO";
            case Types.NCHAR: return "CARACTERE UNICODE";
            case Types.NVARCHAR: return "TEXTO UNICODE";
            case Types.LONGNVARCHAR: return "TEXTO LONGO UNICODE";
            case Types.DATE: return "DATA";
            case Types.TIME: return "HORA";
            case Types.TIMESTAMP: return "DATA/HORA";
            default: return "DESCONHECIDO";
        }
    }

    static class ColumnInfo {
        String name;
        int type;
        int length;
        
        ColumnInfo(String name, int type, int length) {
            this.name = name;
            this.type = type;
            this.length = length;
        }
    }
}