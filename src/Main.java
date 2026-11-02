import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;


public class Main {

    // ACESSO = uma linha do arquivo depois da terceira.
    // Quer dizer: "no momento 8, o programa mexeu na página 4, e foi para ESCREVER".
    // (R = Read/leitura: só olhou o brinquedo.  W = Write/escrita: mexeu e bagunçou.)
    // É só uma caixinha para guardar esses 3 valores.

    static final class Acesso {
        final long pagina;       // qual página foi usada (long porque pode chegar a 2^32)
        final int tempo;         // em que momento do relógio
        final boolean escrita;   // true = escrita (W), false = leitura (R)

        Acesso(long pagina, int tempo, boolean escrita) {
            this.pagina = pagina;
            this.tempo = tempo;
            this.escrita = escrita;
        }
    }

    // ENTRADA = o conteúdo inteiro de UM arquivo TESTE-XX.txt, já "entendido":
    //
    //      64      <- p: quantas páginas existem no total
    //      8       <- m: quantos LUGARES (molduras) a mesinha tem
    //      3       <- c: depois de c tempos sem usar a página, o bit R volta a ser 0
    //      0 0 R   <- página 0, no momento 0, leitura
    //      1 2 R   <- página 1, no momento 2, leitura
    //      ...
    static final class Entrada {
        final long paginas;      // p (só é lido; os algoritmos não precisam dele)
        final long molduras;     // m
        final int ciclo;         // c
        final Acesso[] acessos;  // os acessos, NA MESMA ORDEM do arquivo

        private Entrada(long paginas, long molduras, int ciclo, Acesso[] acessos) {
            this.paginas = paginas;
            this.molduras = molduras;
            this.ciclo = ciclo;
            this.acessos = acessos;
        }


        int capacidadeUtil() {
            return (int) Math.min(molduras, (long) acessos.length);
        }


        static Entrada ler(File arquivo) throws IOException {
            String texto = new String(Files.readAllBytes(arquivo.toPath()), StandardCharsets.UTF_8);
            texto = texto.replace("\uFEFF", "").trim();   // tira um "caractere invisível" que alguns editores põem no começo

            String[] palavras = texto.split("\\s+");
            if (palavras.length < 3 || (palavras.length - 3) % 3 != 0) {
                throw new IOException("Arquivo fora do formato esperado: " + arquivo.getName());
            }

            long p = Long.parseLong(palavras[0]);
            long m = Long.parseLong(palavras[1]);
            int c = Integer.parseInt(palavras[2]);

            int quantidade = (palavras.length - 3) / 3;
            Acesso[] acessos = new Acesso[quantidade];
            for (int i = 0; i < quantidade; i++) {
                int base = 3 + i * 3;                                  // onde começa o grupo i
                long pagina = Long.parseLong(palavras[base]);
                int tempo = Integer.parseInt(palavras[base + 1]);
                boolean escrita = palavras[base + 2].equalsIgnoreCase("W");
                acessos[i] = new Acesso(pagina, tempo, escrita);
            }
            return new Entrada(p, m, c, acessos);
        }
    }


    static File encontrarPasta(String[] args) {
        if (args.length > 0) {
            return new File(args[0]);
        }

        List<File> candidatos = new ArrayList<File>();
        for (File raiz : File.listRoots()) {                            // Windows: C:\, D:\, E:\ ...
            candidatos.add(raiz);
        }
        String usuario = System.getProperty("user.name");
        adicionarSubpastas(candidatos, new File("/run/media/" + usuario));
        adicionarSubpastas(candidatos, new File("/media/" + usuario));
        adicionarSubpastas(candidatos, new File("/media"));
        adicionarSubpastas(candidatos, new File("/mnt"));
        adicionarSubpastas(candidatos, new File("/Volumes"));
        candidatos.add(new File("."));                                  // pasta atual

        for (File pasta : candidatos) {
            if (new File(pasta, "TESTE-01.txt").exists()) {
                return pasta;
            }
        }
        return null;
    }

    private static void adicionarSubpastas(List<File> lista, File base) {
        File[] filhos = base.listFiles();
        if (filhos == null) {
            return;                                                     // a pasta base nem existe
        }
        for (File filho : filhos) {
            if (filho.isDirectory()) {
                lista.add(filho);
            }
        }
    }

    // O PROGRAMA COMEÇA AQUI

    public static void main(String[] args) {
        File pasta = encontrarPasta(args);
        if (pasta == null || !pasta.isDirectory()) {
            System.out.println("Nao achei a pasta com os arquivos TESTE-XX.txt.");
            System.out.println("Passe o caminho em Run > Edit Configurations > Program arguments.");
            return;
        }
        System.out.println("Pasta usada: " + pasta.getAbsolutePath());

        // A ORDEM aqui é a ordem das linhas no arquivo de saída.
        Algoritmos.AlgoritmoSubstituicao[] algoritmos = {
            new Algoritmos.Otimo(), new Algoritmos.NRU(), new Algoritmos.Relogio(), new Algoritmos.WSClock()
        };

        for (int numero = 1; numero <= 10; numero++) {
            String nome = String.format("TESTE-%02d", numero);          // TESTE-01, TESTE-02, ...
            File arquivoEntrada = new File(pasta, nome + ".txt");
            File arquivoSaida = new File(pasta, nome + "-RESULTADO.txt");

            if (!arquivoEntrada.exists()) {
                System.out.println(nome + ": arquivo nao encontrado, pulando.");
                continue;
            }

            try {
                Entrada entrada = Entrada.ler(arquivoEntrada);

                StringBuilder saida = new StringBuilder();
                for (Algoritmos.AlgoritmoSubstituicao algoritmo : algoritmos) {
                    int faltas = algoritmo.simular(entrada);
                    saida.append(faltas).append("\n");                  // uma linha por algoritmo
                    System.out.println(nome + "  " + algoritmo.nome() + ": " + faltas);
                }

                Files.write(arquivoSaida.toPath(), saida.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException erro) {
                System.out.println(nome + ": deu problema -> " + erro.getMessage());
            }
        }
        System.out.println("Pronto");
    }
}
