import java.util.HashMap;


public class Memoria {

    // ------------------------------------------------------------------------------------
    // MOLDURA = um lugarzinho da mesa, com a página sentada nele e as "anotações"
    // que o sistema operacional faz sobre ela:
    //
    //  - ultimoAcesso : a hora em que a página foi usada pela última vez.
    //  - referenciada : "alguém usou essa página recentemente?" (o famoso bit R)
    //  - modificada   : "alguém ESCREVEU nela?"                 (o famoso bit M)
    //  - proximoUso   : só o Ótimo usa: em qual acesso futuro esta página volta a ser usada.
    //
    // Por que o bit M importa? Página NÃO modificada: a cópia do disco ainda é igual, jogar
    // fora é de graça. Página modificada: precisa GRAVAR no disco antes, e isso é lento.
    // ------------------------------------------------------------------------------------
    static final class Moldura {
        final long pagina;
        int ultimoAcesso;
        boolean referenciada;
        boolean modificada;
        int proximoUso;

        /** Cria a moldura no instante em que a página CHEGA na memória (acabou de ser usada). */
        Moldura(long pagina, int tempo, boolean escrita) {
            this.pagina = pagina;
            this.ultimoAcesso = tempo;
            this.referenciada = true;     // acabou de ser usada, então R = 1
            this.modificada = escrita;    // se o acesso foi escrita, já nasce modificada
        }

        /** Alguém usou a página de novo (ela já estava na mesa): atualiza as anotações. */
        void acessar(int tempo, boolean escrita) {
            this.ultimoAcesso = tempo;
            this.referenciada = true;
            if (escrita) {
                this.modificada = true;   // uma vez modificada, fica assim até sair da memória
            }
        }

        /**
         * O valor do bit R AGORA.
         * Regra do trabalho: "ciclo de relógio em que o bit R volta a ser 0". Então o bit R
         * fica ligado durante 'ciclo' tempos depois do último uso e depois desliga sozinho.
         * Também desliga se o algoritmo do Relógio (o "ponteiro") apagar de propósito:
         * é o que a variável 'referenciada' guarda.
         *
         * Exemplo com ciclo = 3: usou no tempo 10 -> R=1 nos tempos 10, 11, 12;
         * a partir do 13 já voltou a 0.
         */
        boolean bitR(int agora, int ciclo) {
            return referenciada && (agora - ultimoAcesso) < ciclo;
        }
    }

    // ------------------------------------------------------------------------------------
    // A MESA
    // ------------------------------------------------------------------------------------
    private final Moldura[] lugares;                                  // as cadeiras
    private int ocupados = 0;                                         // quantas já têm gente
    private final HashMap<Long, Integer> agenda = new HashMap<Long, Integer>();   // página -> lugar

    Memoria(int capacidade) {
        this.lugares = new Moldura[capacidade];
    }

    int capacidade() {
        return lugares.length;
    }

    Moldura get(int posicao) {
        return lugares[posicao];
    }

    /** Devolve a moldura onde a página está, ou null se a página NÃO está na memória. */
    Moldura buscar(long pagina) {
        Integer posicao = agenda.get(pagina);
        return (posicao == null) ? null : lugares[posicao];
    }

    boolean temEspaco() {
        return ocupados < lugares.length;
    }

    /** Coloca a página no próximo lugar vazio (só chame se temEspaco() for true). */
    void carregar(Moldura nova) {
        lugares[ocupados] = nova;
        agenda.put(nova.pagina, ocupados);
        ocupados++;
    }

    /** Tira quem está em 'posicao' e coloca 'nova' no mesmo lugar. */
    void substituir(int posicao, Moldura nova) {
        agenda.remove(lugares[posicao].pagina);   // o antigo sai da agenda
        lugares[posicao] = nova;
        agenda.put(nova.pagina, posicao);         // o novo entra na agenda
    }
}
