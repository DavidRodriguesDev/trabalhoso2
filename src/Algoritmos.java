import java.util.HashMap;


public class Algoritmos {

    // ====================================================================================
    // A RECEITA GERAL
    // ====================================================================================
    abstract static class AlgoritmoSubstituicao {

        /** Nome do algoritmo (só para escrever na tela). */
        abstract String nome();

        /**
         * A pergunta do milhão: a mesa está cheia, QUEM levanta?
         * Devolve a POSIÇÃO (o número do lugar) da página que será retirada.
         *
         * @param memoria a mesa
         * @param agora   o momento (tempo) do acesso que causou a falta
         */
        abstract int escolherVitima(Memoria memoria, int agora);

        /** Gancho: chamado ANTES de começar cada arquivo, para o algoritmo zerar suas anotações. */
        void preparar(Main.Entrada entrada) {
        }

        /** Gancho: chamado toda vez que uma página é usada (acerto) ou chega (falta). Só o Ótimo usa. */
        void aoAcessar(Memoria.Moldura moldura, int indiceDoAcesso) {
        }

        /** Roda a simulação inteira para uma Entrada e devolve o TOTAL DE FALTAS DE PÁGINA. */
        final int simular(Main.Entrada entrada) {
            preparar(entrada);

            Memoria memoria = new Memoria(entrada.capacidadeUtil());
            int faltas = 0;

            for (int i = 0; i < entrada.acessos.length; i++) {
                Main.Acesso acesso = entrada.acessos[i];

                Memoria.Moldura moldura = memoria.buscar(acesso.pagina);

                if (moldura != null) {
                    // ACERTO: a página já estava na mesa. Só atualizamos as anotações.
                    moldura.acessar(acesso.tempo, acesso.escrita);
                    aoAcessar(moldura, i);
                    continue;
                }

                // FALTA DE PÁGINA: o convidado não estava na mesa.
                faltas++;
                Memoria.Moldura nova = new Memoria.Moldura(acesso.pagina, acesso.tempo, acesso.escrita);
                aoAcessar(nova, i);

                if (memoria.temEspaco()) {
                    memoria.carregar(nova);                               // tinha cadeira vazia
                } else {
                    int posicao = escolherVitima(memoria, acesso.tempo);  // mesa cheia: alguém levanta
                    memoria.substituir(posicao, nova);
                }
            }
            return faltas;
        }
    }

    // 1) ALGORITMO ÓTIMO
    //
    // Quando a mesa está cheia, levanta quem vai demorar MAIS tempo para ser chamado de novo
    // (ou quem nunca mais vai ser chamado).
    //
    // Exemplo de bebê: 3 brinquedos no berço e chega um novo. O chocalho você vai querer
    // daqui a 1 minuto; o ursinho, daqui a 5; a girafa, nunca mais. Tira a girafa!
    //
    // "Ótimo" porque ninguém erra menos que ele. O problema: precisa SABER O FUTURO, e um
    // sistema operacional de verdade não sabe. Aqui conseguimos porque o arquivo de teste já
    // mostra todos os acessos. Serve como "nota máxima" para comparar os outros.
    static final class Otimo extends AlgoritmoSubstituicao {

        private static final int NUNCA = Integer.MAX_VALUE;   // "essa página nunca mais será usada"
        private int[] proximoUsoDeCadaAcesso;

        @Override
        String nome() {
            return "Otimo";
        }

        @Override
        void preparar(Main.Entrada entrada) {
            // Antes de começar, descobrimos o futuro: para cada acesso i, em qual acesso
            // futuro a MESMA página será usada de novo. Truque: andamos de trás para frente,
            // anotando a última vez que vimos cada página.
            Main.Acesso[] acessos = entrada.acessos;
            proximoUsoDeCadaAcesso = new int[acessos.length];
            HashMap<Long, Integer> vistoPorUltimo = new HashMap<Long, Integer>();
            for (int i = acessos.length - 1; i >= 0; i--) {
                Integer depois = vistoPorUltimo.get(acessos[i].pagina);
                proximoUsoDeCadaAcesso[i] = (depois == null) ? NUNCA : depois;
                vistoPorUltimo.put(acessos[i].pagina, i);
            }
        }

        @Override
        void aoAcessar(Memoria.Moldura moldura, int indiceDoAcesso) {
            // Toda vez que a página é usada, anotamos QUANDO ela será usada de novo.
            moldura.proximoUso = proximoUsoDeCadaAcesso[indiceDoAcesso];
        }

        @Override
        int escolherVitima(Memoria memoria, int agora) {
            int escolhida = 0;
            for (int i = 1; i < memoria.capacidade(); i++) {
                // Quem demora mais para ser usado de novo (maior proximoUso) é o escolhido.
                if (memoria.get(i).proximoUso > memoria.get(escolhida).proximoUso) {
                    escolhida = i;
                }
            }
            return escolhida;
        }
    }

    // 2) NRU = Not Recently Used  ("Não Usada Recentemente")
    //
    // Separa as páginas da mesa em 4 "turminhas", olhando os bits R e M, e tira alguém da
    // turminha MAIS "sem importância":
    //
    //      Classe 0:  R=0 e M=0  -> não usada recentemente e limpa     (melhor para tirar)
    //      Classe 1:  R=0 e M=1  -> não usada recentemente, mas suja
    //      Classe 2:  R=1 e M=0  -> usada recentemente, mas limpa
    //      Classe 3:  R=1 e M=1  -> usada recentemente e suja          (pior para tirar)
    //
    // Por quê nessa ordem? Página usada há pouco provavelmente vai ser usada de novo; página
    // suja custa gravar no disco. As duas coisas são ruins de tirar.
    //
    // Regra: procure a MENOR classe que tenha alguém e tire dela. Se tem várias pessoas na
    // mesma turminha, tiramos a PRIMEIRA, olhando os lugares do 0 em diante (o NRU original
    // sorteia; aqui usamos "a primeira" para o resultado ser sempre o mesmo).
    //
    // Conta da classe: 2*R + M   (R vale 2 pontos, M vale 1 ponto).
    static final class NRU extends AlgoritmoSubstituicao {

        private int ciclo;

        @Override
        String nome() {
            return "NRU";
        }

        @Override
        void preparar(Main.Entrada entrada) {
            ciclo = entrada.ciclo;
        }

        @Override
        int escolherVitima(Memoria memoria, int agora) {
            int escolhida = 0;
            int menorClasse = Integer.MAX_VALUE;

            for (int i = 0; i < memoria.capacidade(); i++) {
                Memoria.Moldura m = memoria.get(i);
                int classe = (m.bitR(agora, ciclo) ? 2 : 0) + (m.modificada ? 1 : 0);

                if (classe < menorClasse) {    // "<" (e não "<=") garante que, num empate, fica a primeira
                    menorClasse = classe;
                    escolhida = i;
                }
            }
            return escolhida;
        }
    }

    // 3) ALGORITMO DO RELÓGIO (Clock, ou "Segunda Chance")
    //
    // As páginas ficam sentadas em CÍRCULO, como as horas num relógio, e um PONTEIRO aponta
    // para uma delas. O ponteiro só anda para a frente, sempre no mesmo sentido.
    //
    // Quando a mesa está cheia e chega um novo, olhamos onde o ponteiro aponta:
    //   - Bit R = 1 (usada recentemente): ganha uma SEGUNDA CHANCE. Apagamos o R dela
    //     (vira 0) e o ponteiro anda para a próxima.
    //   - Bit R = 0: é ESSA que levanta. O novo senta no lugar dela e o ponteiro anda
    //     uma casa.
    //
    // Como cada passada por uma página com R=1 apaga o R, em algum momento o ponteiro dá a
    // volta e encontra alguém com R=0. Nunca fica rodando para sempre.
    static final class Relogio extends AlgoritmoSubstituicao {

        private int ciclo;
        private int ponteiro;       // em qual lugar o ponteiro está apontando

        @Override
        String nome() {
            return "Relogio";
        }

        @Override
        void preparar(Main.Entrada entrada) {
            ciclo = entrada.ciclo;
            ponteiro = 0;           // cada arquivo de teste começa com o ponteiro no lugar 0
        }

        @Override
        int escolherVitima(Memoria memoria, int agora) {
            int total = memoria.capacidade();

            // Enquanto a página apontada tiver R=1: dá segunda chance (apaga R) e anda.
            while (memoria.get(ponteiro).bitR(agora, ciclo)) {
                memoria.get(ponteiro).referenciada = false;       // apagou o R
                ponteiro = (ponteiro + 1) % total;                // "% total" volta ao 0 depois do último lugar
            }

            int escolhida = ponteiro;                             // achamos alguém com R=0: essa sai
            ponteiro = (ponteiro + 1) % total;                    // ponteiro já fica na próxima
            return escolhida;
        }
    }

    // 4) WSCLOCK = Working Set + Clock  (Conjunto de Trabalho + Relógio)
    //
    // É o Relógio com um cuidado a mais: olhar a IDADE da página, "há quanto tempo ela foi
    // usada pela última vez". O "conjunto de trabalho" é o grupo de páginas que o programa
    // está usando de verdade AGORA. Página usada há pouco provavelmente faz parte dele, então
    // NÃO queremos tirá-la. Página "velha" pode sair.
    //
    // Quanto é "velha"? Chamamos o limite de TAU. O enunciado não dá valor para TAU, então
    // usamos o próprio ciclo c do arquivo (TAU = c). Idade maior que TAU = velha.
    //
    // O ponteiro olha, página por página (no máximo uma volta completa):
    //   1. R = 1 (usada recentemente) -> está no conjunto de trabalho. Apaga o R e segue.
    //   2. R = 0, idade > TAU e LIMPA (M=0) -> velha e de graça para tirar. ESSA SAI. Pronto!
    //   3. R = 0, idade > TAU mas SUJA (M=1) -> velha, mas custaria gravar no disco. Um sistema
    //      de verdade "agenda" a gravação e segue em frente. Nós só seguimos em frente.
    //   4. R = 0 mas idade <= TAU -> ainda é nova. Segue em frente.
    //
    // Se o ponteiro deu a volta inteira e NINGUÉM serviu, tiramos a página onde ele começou.
    // Depois de escolher, o ponteiro anda uma casa para a frente.
    static final class WSClock extends AlgoritmoSubstituicao {

        private int ciclo;
        private int tau;
        private int ponteiro;

        @Override
        String nome() {
            return "WSClock";
        }

        @Override
        void preparar(Main.Entrada entrada) {
            ciclo = entrada.ciclo;
            tau = entrada.ciclo;     // TAU = c (suposição explicada acima)
            ponteiro = 0;
        }

        @Override
        int escolherVitima(Memoria memoria, int agora) {
            int total = memoria.capacidade();
            int ondeComecou = ponteiro;

            for (int passo = 0; passo < total; passo++) {         // no máximo UMA volta completa
                Memoria.Moldura m = memoria.get(ponteiro);

                if (m.bitR(agora, ciclo)) {
                    // Caso 1: usada recentemente. Apaga o R e segue.
                    m.referenciada = false;
                } else {
                    int idade = agora - m.ultimoAcesso;
                    if (idade > tau && !m.modificada) {
                        // Caso 2: velha e limpa. É essa!
                        int escolhida = ponteiro;
                        ponteiro = (ponteiro + 1) % total;
                        return escolhida;
                    }
                    // Casos 3 e 4: suja ou ainda nova. Segue em frente sem mexer.
                }
                ponteiro = (ponteiro + 1) % total;
            }

            // Deu a volta e ninguém serviu: sai a página onde começamos.
            ponteiro = (ondeComecou + 1) % total;
            return ondeComecou;
        }
    }
}
