package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SourceDepot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `deposer-vigiechiro` (#1043) : téléverse la nuit d'un passage sur la plateforme VigieChiro via le
/// **moteur reprenable** ([DepotVigieChiro], #982) : participation créée ou réutilisée, plan
/// `depot_unite` persisté, seuls les fichiers **manquants** sont (re)téléversés : la commande est
/// **relançable** telle quelle après une coupure. À ne pas confondre avec `deposer`, le **marquage
/// manuel** (téléversement fait sur le site web).
///
/// **Ce qui est déposé** : le **même défaut que M-Lot**, c'est-à-dire le réglage `depot.mode` (#1997,
/// Réglages ▸ Dépôt) : archives ZIP par défaut, ou séquences WAV. Ce n'est plus l'espace disque qui
/// tranche : il ne fait plus que **refuser** un dépôt ZIP qu'il ne peut pas honorer. `--archives` et
/// `--wav` priment sur le réglage pour un dépôt ponctuel.
///
/// Le mode n'est pas qu'une question de vitesse : en ZIP, la plateforme détruit l'archive après
/// extraction sans conserver les sons (#1244), donc l'audio n'est pas récupérable côté serveur et la
/// participation ne pourra pas être relancée. Cf. [fr.univ_amu.iut.lot.model.ModeDepot].
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la **connexion
/// enregistrée** dans l'application (préférer la variable d'environnement à `--token`, qui laisse le
/// jeton dans l'historique du shell).
///
/// Le dépôt **ne déclenche pas** le traitement serveur : lancer ensuite `lancer-traitement-vigiechiro`
/// (équivalent du bouton « Lancer la participation »).
///
/// Sortie : une ligne par fichier téléversé (`+`) ou en échec (`!` + raison), puis le bilan. Code
/// retour `0` seulement si le dépôt est **complet** (scriptable) ; `1` si des fichiers restent à
/// reprendre.
@Command(
        name = "deposer-vigiechiro",
        description = "Téléverse un passage sur Vigie-Chiro (reprenable : seuls les fichiers manquants repartent).")
public final class DeposerVigieChiro implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage à téléverser (dépôt préparé : statut « Prêt à déposer » ou « Dépôt en cours »).")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton Vigie-Chiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Option(
            names = "--archives",
            description = "Force le dépôt des archives ZIP générées (depot/*.zip). Par défaut, elles sont déjà"
                    + " privilégiées si présentes (comme M-Lot) ; cette option échoue si elles manquent.")
    private boolean archives;

    @Option(
            names = "--wav",
            description = "Force le dépôt des séquences WAV une à une, même si des archives ZIP existent"
                    + " (par défaut : ZIP si présentes, repli WAV sinon). Incompatible avec --archives.")
    private boolean wav;

    @Spec
    private CommandSpec spec;

    private final ServiceLot serviceLot;
    private final Optional<DepotVigieChiro> depot;

    @Inject
    public DeposerVigieChiro(ServiceLot serviceLot, Optional<DepotVigieChiro> depot) {
        this.serviceLot = Objects.requireNonNull(serviceLot, "serviceLot");
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    @Override
    public Integer call() {
        DepotVigieChiro moteur = depot.orElseThrow(
                () -> new RegleMetierException("Dépôt Vigie-Chiro indisponible dans ce contexte d'exécution."));
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel : consulté par le client à chaque requête (cf. ConnexionModule), sans rien
            // persister : la connexion enregistrée de l'application n'est pas modifiée.
            System.setProperty("vigiechiro.token", token);
        }
        SourceDepot source = choisirSource();
        PrintWriter sortie = spec.commandLine().getOut();
        BilanDepot bilan = moteur.deposer(idPassage, source, () -> false, new SuiviConsole(sortie));
        sortie.println(rendreBilan(bilan));
        return bilan.estComplet() ? 0 : 1;
    }

    /// Choix de la source à téléverser, **aligné sur M-Lot** : les deux options imposent un [ModeDepot],
    /// le réglage `depot.mode` servant de défaut quand aucune n'est donnée.
    ///
    /// `--archives` ne ramène **pas** la liste des ZIP présents sur le disque : il force le mode ZIP, dont
    /// la source est régénérable (#1994). Sans cela, l'option échouait dès qu'une archive avait été
    /// libérée : ce qui est désormais le cas normal après un dépôt, puisque le pipeline libère au fil de
    /// l'eau (#1995). Elle reproduisait donc exactement le défaut que ce chantier a fermé côté service.
    private SourceDepot choisirSource() {
        if (archives) {
            return serviceLot.sourceDepot(idPassage, ModeDepot.ARCHIVES_ZIP);
        }
        if (wav) {
            return serviceLot.sourceDepot(idPassage, ModeDepot.SEQUENCES_WAV);
        }
        return serviceLot.sourceDepotParDefaut(idPassage);
    }

    /// Bilan final : participation, fichiers téléversés cette fois-ci, **volume en ligne**, reste
    /// éventuel. Fonction pure (testable sans base ni réseau).
    ///
    /// Le volume vient de la clôture #2802, passe 2 : l'écran le dit depuis #2653 - le téléverseur le
    /// mesurait déjà pour choisir sa voie d'envoi - et la commande le taisait. Une capacité livrée d'un
    /// seul côté est à moitié livrée.
    static String rendreBilan(BilanDepot bilan) {
        String volume = volumeLisible(bilan);
        if (bilan.estComplet()) {
            return "Dépôt complet : " + bilan.deposees() + " fichier(s) téléversé(s)" + volume + " (participation "
                    + bilan.participationId() + "). Passage marqué « Déposé ».";
        }
        return "Dépôt INCOMPLET : " + bilan.deposees() + " fichier(s) téléversé(s)" + volume + ", "
                + bilan.echecs().size()
                + " en échec (participation " + bilan.participationId() + ")." + quoiFaireDesEchecs(bilan);
    }

    /// Ce qu'on conseille des échecs, **et seulement ce que la commande peut tenir**.
    ///
    /// « Relancez la commande pour ne reprendre que les manquants » était dit de tous les échecs. Or une
    /// archive **refusée** ne repart pas : la relance la refuserait de la même façon. La CLI promettait
    /// donc, en une phrase, ce que l'écran avait cessé de promettre en #3687 (#3962).
    private static String quoiFaireDesEchecs(BilanDepot bilan) {
        int reprenables = bilan.reprenables().size();
        List<EchecUnite> refuses = bilan.refusesDefinitivement();
        StringBuilder conseil = new StringBuilder();
        if (reprenables > 0) {
            conseil.append(" Relancez la commande pour reprendre les ")
                    .append(reprenables)
                    .append(" manquante(s).");
        }
        if (!refuses.isEmpty()) {
            conseil.append(" ")
                    .append(refuses.size())
                    .append(" refusée(s) par Vigie-Chiro, que la relance ne")
                    .append(" reprendra pas :");
            refuses.forEach(refus -> conseil.append(" ")
                    .append(refus.identifiantUnite())
                    .append(" (")
                    .append(refus.raison())
                    .append(")"));
            conseil.append(".");
            if (refuses.stream().allMatch(EchecUnite::seRearmeParUneReconnexion)) {
                conseil.append(" Reconnectez-vous, puis relancez : elles redeviendront reprenables.");
            }
        }
        return conseil.toString();
    }

    /// Le volume en ligne, **s'il a été mesuré**. Rien à zéro : un « 0 Ko téléversé » annoncerait une
    /// absence, la faute corrigée sur les volumes d'import (#2677) et les validations (#2695).
    private static String volumeLisible(BilanDepot bilan) {
        return bilan.octetsDeposes() > 0 ? " (" + Formats.octetsLisibles(bilan.octetsDeposes()) + ")" : "";
    }

    /// Ligne de plan : combien d'unités sont à téléverser, combien sont déjà en ligne (reprise). Fonction
    /// pure (testable sans base ni réseau).
    static String rendrePlan(List<DepotUnite> unites) {
        long dejaDeposees = unites.stream()
                .filter(unite -> unite.statut() == StatutDepotUnite.DEPOSE)
                .count();
        String reprise = dejaDeposees == 0 ? "" : " (" + dejaDeposees + " déjà en ligne, reprise)";
        return "Plan de dépôt : " + unites.size() + " fichier(s)" + reprise + ".";
    }

    /// Suivi console du dépôt : une ligne par unité, écrite au fil de l'eau (le moteur émet sur le fil
    /// d'appel : pas de relais nécessaire en CLI, contrairement à l'IHM).
    /// Visible du paquet pour que le test puisse vérifier ce que chaque évènement imprime - la reprise
    /// notamment, qu aucun test ne pouvait atteindre tant que ce type était privé.
    record SuiviConsole(PrintWriter sortie) implements SuiviDepot {

        @Override
        public void planEtabli(List<DepotUnite> unites) {
            sortie.println(rendrePlan(unites));
        }

        @Override
        public void uniteDemarree(String identifiant) {
            // Silencieux : la ligne de fin (déposée / échec) suffit, le dépôt est séquentiel.
        }

        @Override
        public void uniteDeposee(DepotUnite unite) {
            sortie.println("  + " + unite.identifiantUnite());
        }

        @Override
        public void uniteEchouee(String identifiant, String raison, boolean definitif) {
            sortie.println("  ! " + identifiant + " : " + raison);
        }

        /// Parité avec l'IHM (clôture #2350) : depuis le réessai gradué (#2354), une coupure momentanée
        /// n'échoue plus, elle **attend**. L'écran le dit par une mention discrète ; la ligne de commande
        /// se taisait, et une temporisation de trente secondes y passait pour un blocage - exactement ce
        /// qu'un utilisateur interrompt au clavier, annulant un dépôt qui allait aboutir.
        @Override
        public void uniteReprise(String identifiant, java.time.Duration delai) {
            sortie.println("  ~ " + identifiant + " : nouvelle tentative dans " + delai.toSeconds() + " s");
        }
    }
}
