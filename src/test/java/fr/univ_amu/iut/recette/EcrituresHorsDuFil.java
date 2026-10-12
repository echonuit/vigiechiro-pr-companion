package fr.univ_amu.iut.recette;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import javafx.beans.property.ReadOnlyProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

/// Le registre des écritures de propriété faites hors du fil JavaFX (#6142).
///
/// JavaFX ne vérifie le fil qu'en quelques points : la liste des enfants d'une scène montrée, une
/// prise de vue, l'ouverture d'une fenêtre. Écrire le texte d'un champ depuis une tâche de fond ne
/// lève rien, et le défaut ne se voit qu'au hasard d'une pile du rendu muette sur l'écrivain (#6138).
///
/// Ce registre pose une sonde à l'entrée des deux méthodes par lesquelles toute propriété JavaFX
/// annonce un changement : `markInvalid` des `*PropertyBase` et du texte d'un champ de saisie, et
/// `fireValueChangedEvent` des `ReadOnly*PropertyBase`. Une écriture directe, une liaison qui se
/// propage et un écouteur qui réécrit un nœud y passent tous, sur le fil de l'écrivain. La sonde
/// ne lève jamais : JavaFX avale ce que lève un écouteur, donc on relève ici et
/// [SansEcritureHorsDuFil] affirme après.
///
/// Ce qu'il retient : une propriété dont le porteur est un nœud attaché à une scène d'une fenêtre
/// montrée, invalidée depuis un autre fil que celui de JavaFX.
///
/// Ce qu'il ne retient pas, et ne peut pas voir : un nœud pas encore attaché, qu'on a le droit de
/// bâtir hors du fil ; une propriété de modèle de vue, tant que rien de la scène n'en dépend ; une
/// liste observable modifiée sans qu'aucune propriété ne bouge ; une lecture hors du fil.
public final class EcrituresHorsDuFil {

    private static final String FIL_JAVAFX = "JavaFX Application Thread";
    private static final String RACINE_DU_PRODUIT = "fr.univ_amu.iut.";
    private static final String CLE_DU_JOURNAL = "vigiechiro.espion.journal";

    private static final List<String> BASES_INSCRIPTIBLES =
            List.of("Boolean", "Integer", "Long", "Float", "Double", "String", "Object", "List", "Set", "Map");

    private static final List<Releve> RELEVES = new CopyOnWriteArrayList<>();
    private static final LongAdder SUR_LE_FIL = new LongAdder();
    private static final LongAdder HORS_DU_FIL = new LongAdder();
    private static final LongAdder HORS_DU_FIL_SUR_UN_NOEUD_LIBRE = new LongAdder();
    private static final LongAdder HORS_DU_FIL_SANS_NOEUD = new LongAdder();
    private static final Map<String, LongAdder> FILS_ETRANGERS = new ConcurrentHashMap<>();

    private static volatile boolean actif;
    private static volatile Thread filJavaFx;
    private static volatile Thread filDuBanc;
    private static boolean sondePosee;

    private EcrituresHorsDuFil() {}

    /// Une écriture relevée : qui a été écrit, par quel fil, et la pile de l'écrivain.
    ///
    /// `parLeBanc` distingue le fil du test de tout autre : un banc qui écrit un nœud depuis son
    /// propre fil est un défaut du banc, pas du produit.
    public record Releve(String noeud, String propriete, String fil, boolean parLeBanc, Throwable pile) {

        /// La première ligne de la pile qui appartient au produit : c'est l'écrivain.
        public String site() {
            for (StackTraceElement ligne : pile.getStackTrace()) {
                if (ligne.getClassName().startsWith(RACINE_DU_PRODUIT)
                        && !ligne.getClassName().startsWith(EcrituresHorsDuFil.class.getName())) {
                    return ligne.toString();
                }
            }
            return "aucune ligne du produit dans la pile";
        }

        /// Toutes les lignes du produit dans la pile, de l'écriture jusqu'à la tâche : la première
        /// est souvent un écouteur, et c'est plus bas qu'on lit qui a écrit, et pour quelle tâche.
        public String chaine() {
            List<String> lignes = new ArrayList<>();
            for (StackTraceElement ligne : pile.getStackTrace()) {
                if (ligne.getClassName().startsWith(RACINE_DU_PRODUIT)
                        && !ligne.getClassName().startsWith(EcrituresHorsDuFil.class.getName())) {
                    lignes.add(ligne.getFileName() + ":" + ligne.getLineNumber());
                }
            }
            return String.join(" < ", lignes);
        }

        String cle() {
            return noeud + "|" + propriete + "|" + site();
        }
    }

    /// Ce que le registre a regardé pendant un cas : sans ce compte, un zéro ne prouve rien.
    public record Population(
            long surLeFil, long horsDuFil, long surUnNoeudLibre, long sansNoeud, Map<String, Long> filsEtrangers) {}

    /// La sonde, recopiée par ByteBuddy à l'entrée des méthodes d'invalidation.
    public static final class Sonde {

        private Sonde() {}

        @Advice.OnMethodEnter
        public static void entree(@Advice.This Object propriete) {
            EcrituresHorsDuFil.relever(propriete);
        }
    }

    /// Ouvre le relevé pour un cas. Le fil appelant est tenu pour celui du banc.
    static synchronized void ouvrir() {
        poserLaSonde();
        RELEVES.clear();
        SUR_LE_FIL.reset();
        HORS_DU_FIL.reset();
        HORS_DU_FIL_SUR_UN_NOEUD_LIBRE.reset();
        HORS_DU_FIL_SANS_NOEUD.reset();
        FILS_ETRANGERS.clear();
        filDuBanc = Thread.currentThread();
        actif = true;
    }

    /// Ferme le relevé et rend ce qui a été vu, une ligne par nœud, propriété et site d'écriture,
    /// l'écriture d'origine en tête.
    static List<Releve> fermer() {
        actif = false;
        Map<String, Releve> distincts = new LinkedHashMap<>();
        for (Releve releve : RELEVES) {
            distincts.putIfAbsent(releve.cle(), releve);
        }
        RELEVES.clear();
        // La pile la plus courte est celle de l'écriture d'origine : les autres en découlent, par
        // un écouteur ou par une liaison, et l'empilent.
        List<Releve> tries = new ArrayList<>(distincts.values());
        tries.sort(Comparator.comparingInt(releve -> releve.pile().getStackTrace().length));
        return tries;
    }

    static Population population() {
        Map<String, Long> fils = new LinkedHashMap<>();
        FILS_ETRANGERS.forEach((nom, compte) -> fils.put(nom, compte.sum()));
        return new Population(
                SUR_LE_FIL.sum(),
                HORS_DU_FIL.sum(),
                HORS_DU_FIL_SUR_UN_NOEUD_LIBRE.sum(),
                HORS_DU_FIL_SANS_NOEUD.sum(),
                fils);
    }

    /// Appelée par la sonde, sur le fil de l'écrivain. Ne lève rien et n'écrit aucune propriété.
    public static void relever(Object source) {
        if (!actif) {
            return;
        }
        Thread courant = Thread.currentThread();
        if (courant == filJavaFx) {
            SUR_LE_FIL.increment();
            return;
        }
        if (filJavaFx == null && FIL_JAVAFX.equals(courant.getName())) {
            filJavaFx = courant;
            SUR_LE_FIL.increment();
            return;
        }
        HORS_DU_FIL.increment();
        FILS_ETRANGERS.computeIfAbsent(famille(courant), _ -> new LongAdder()).increment();
        if (!(source instanceof ReadOnlyProperty<?> propriete) || !(propriete.getBean() instanceof Node noeud)) {
            HORS_DU_FIL_SANS_NOEUD.increment();
            return;
        }
        if (!estMontre(noeud)) {
            HORS_DU_FIL_SUR_UN_NOEUD_LIBRE.increment();
            return;
        }
        RELEVES.add(new Releve(
                decrire(noeud), propriete.getName(), courant.toString(), courant == filDuBanc, new Throwable()));
    }

    private static boolean estMontre(Node noeud) {
        Scene scene = noeud.getScene();
        if (scene == null) {
            return false;
        }
        Window fenetre = scene.getWindow();
        return fenetre != null && fenetre.isShowing();
    }

    /// Le nœud par son type et son identifiant, et à défaut par son premier ancêtre identifié.
    private static String decrire(Node noeud) {
        String type = noeud.getClass().getSimpleName();
        if (type.isEmpty()) {
            type = noeud.getClass().getName();
        }
        if (noeud.getId() != null) {
            return type + "#" + noeud.getId();
        }
        for (Node ancetre = noeud.getParent(); ancetre != null; ancetre = ancetre.getParent()) {
            if (ancetre.getId() != null) {
                return type + " sous " + ancetre.getClass().getSimpleName() + "#" + ancetre.getId();
            }
        }
        return type + " sans ancêtre identifié";
    }

    /// Le nom du fil sans son numéro : cent fils virtuels de la même tâche font une famille.
    private static String famille(Thread fil) {
        String nom = fil.getName();
        if (nom.isEmpty()) {
            return fil.isVirtual() ? "fil virtuel sans nom" : "fil sans nom";
        }
        return nom.replaceAll("\\d+", "N");
    }

    private static void poserLaSonde() {
        if (sondePosee) {
            return;
        }
        List<String> cibles = new ArrayList<>();
        for (String base : BASES_INSCRIPTIBLES) {
            cibles.add("javafx.beans.property." + base + "PropertyBase");
            cibles.add("javafx.beans.property.ReadOnly" + base + "PropertyBase");
        }
        // La seule propriété inscriptible de JavaFX qui n'hérite pas d'une base : le texte d'un champ
        // de saisie, lu par javap dans javafx-controls.
        cibles.add("javafx.scene.control.TextInputControl$TextProperty");
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .ignore(ElementMatchers.none())
                .type(ElementMatchers.namedOneOf(cibles.toArray(String[]::new)))
                .transform((batisseur, type, _, _, _) -> batisseur.visit(Advice.to(Sonde.class)
                        .on(
                                type.getSimpleName().startsWith("ReadOnly")
                                        ? ElementMatchers.named("fireValueChangedEvent")
                                        : ElementMatchers.named("markInvalid"))))
                .installOnByteBuddyAgent();
        sondePosee = true;
    }

    /// Verse le relevé d'un cas dans le journal, si la clé `vigiechiro.espion.journal` en nomme un.
    ///
    /// Un fichier par JVM : surefire répartit les classes sur plusieurs forks, qui s'écraseraient.
    static void consigner(String classe, String cas, List<Releve> releves, Population population) {
        String dossier = System.getProperty(CLE_DU_JOURNAL);
        if (dossier == null || dossier.isBlank()) {
            return;
        }
        List<String> lignes = new ArrayList<>();
        lignes.add(String.join(
                "\t",
                "CAS",
                classe,
                cas,
                Long.toString(population.surLeFil()),
                Long.toString(population.horsDuFil()),
                Long.toString(population.surUnNoeudLibre()),
                Long.toString(population.sansNoeud()),
                population.filsEtrangers().toString()));
        for (Releve releve : releves) {
            lignes.add(String.join(
                    "\t",
                    releve.parLeBanc() ? "BANC" : "PRODUIT",
                    classe,
                    cas,
                    releve.noeud(),
                    releve.propriete(),
                    releve.fil(),
                    releve.site(),
                    releve.chaine()));
        }
        try {
            Path journal = Path.of(dossier, ProcessHandle.current().pid() + ".tsv");
            Files.createDirectories(journal.getParent());
            Files.write(journal, lignes, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("le journal de l'espion n'a pas pu s'écrire", e);
        }
    }
}
