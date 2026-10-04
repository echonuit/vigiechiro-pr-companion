package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
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

/// `deposer-vigiechiro` (#1043) : téléverse la nuit d'un passage sur la plateforme via le **moteur
/// reprenable** ([DepotVigieChiro], #982). Participation créée ou réutilisée, plan `depot_unite`
/// persisté, seuls les fichiers manquants sont téléversés : la commande est **relançable** telle quelle
/// après une coupure. À ne pas confondre avec `deposer`, le marquage manuel.
///
/// **Ce qui est déposé** suit le réglage `depot.mode` (#1997), comme M-Lot : séquences WAV par défaut
/// (#5677), ou archives ZIP, `--archives` et `--wav` primant pour un dépôt ponctuel. L'espace disque ne tranche
/// plus, il ne fait que **refuser** un dépôt ZIP qu'il ne peut pas honorer. Le mode n'est pas qu'une
/// question de vitesse : en ZIP la plateforme détruit l'archive après extraction sans conserver les sons
/// (#1244), donc l'audio n'est pas récupérable côté serveur.
///
/// **Jeton** : `--token`, sinon `VIGIECHIRO_TOKEN`, sinon la connexion enregistrée dans l'application ;
/// préférer la variable d'environnement, `--token` laissant le jeton dans l'historique du shell.
///
/// Le dépôt **ne déclenche pas** le traitement serveur : lancer ensuite `lancer-traitement-vigiechiro`.
/// Sortie : une ligne par fichier (`+` ou `!` avec la raison), puis le bilan ; code `0` seulement si le
/// dépôt est **complet**, `1` s'il reste à reprendre.
@Command(
        name = "deposer-vigiechiro",
        description = "Téléverse un passage sur Vigie-Chiro (reprenable : seuls les fichiers manquants repartent).")
public final class DeposerVigieChiro implements Callable<Integer> {

    /// Ce qui s'applique à un refus du stockage (#5598) : chaque relance redéclare le fichier, donc
    /// redemande des URL signées neuves, et le dépôt manuel reste possible.
    private static final String GESTE_STOCKAGE = "se reconnecter n'y changera rien. Relancez la commande,"
            + " qui redemande de nouvelles autorisations d'envoi ; si le refus persiste, déposez-les"
            + " manuellement depuis le dossier de la nuit.";

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
            description = "Force le dépôt en archives ZIP, quel que soit le réglage du mode de dépôt. Un dépôt"
                    + " déjà entamé garde son mode sans cette option.")
    private boolean archives;

    @Option(
            names = "--wav",
            description = "Force le dépôt des séquences WAV une à une, quel que soit le réglage du mode de"
                    + " dépôt (WAV par défaut). Incompatible avec --archives.")
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
                    .append(" refusée(s) par Vigie-Chiro, que relancer telles quelles")
                    .append(" ferait refuser de même :");
            refuses.forEach(refus -> conseil.append(" ")
                    .append(refus.identifiantUnite())
                    .append(" (")
                    .append(refus.raison())
                    .append(")"));
            conseil.append(".").append(gestesDesRefus(refuses));
        }
        return conseil.toString();
    }

    /// Un geste par cause, avec la part qu'il concerne quand les causes sont mêlées (#5598). Les mêmes
    /// gestes que l'écran (`CompteRenduChiffreDepot`), dans les mots de la commande (ADR 0014).
    private static String gestesDesRefus(List<EchecUnite> refuses) {
        // Le prédicat reste la seule autorité sur « une reconnexion répare ceci » (#3961).
        long droits =
                refuses.stream().filter(EchecUnite::seRearmeParUneReconnexion).count();
        long stockage = compter(refuses, CauseRefus.STOCKAGE);
        long contenu = refuses.size() - droits - stockage;
        if (droits == refuses.size()) {
            return " Reconnectez-vous, puis relancez : elles redeviendront reprenables.";
        }
        if (contenu == refuses.size()) {
            // Geste VÉRIFIÉ (#3946) : la relance retente bien ces unités - `restantes()` rend « tout
            // sauf déposé » et rien ne les écarte sur leur drapeau. Ce qu'il faut changer, c'est le
            // CONTENU de l'archive, pas la façon de la renvoyer.
            return " Régénérez les archives, puis relancez : les nouvelles repartiront.";
        }
        if (stockage == refuses.size()) {
            return " Refusées par le stockage de Vigie-Chiro : " + GESTE_STOCKAGE;
        }
        StringBuilder gestes = new StringBuilder();
        if (droits > 0) {
            gestes.append(" ")
                    .append(droits)
                    .append(accord(droits, " d'entre elles tenait", " d'entre elles tenaient"))
                    .append(" à vos droits : reconnectez-vous, puis relancez.");
        }
        if (stockage > 0) {
            gestes.append(" ")
                    .append(stockage)
                    .append(accord(stockage, " d'entre elles a été refusée", " d'entre elles ont été refusées"))
                    .append(" par le stockage : ")
                    .append(GESTE_STOCKAGE);
        }
        if (contenu > 0) {
            gestes.append(" ")
                    .append(contenu)
                    .append(accord(contenu, " d'entre elles a", " d'entre elles ont"))
                    .append(" un contenu refusé : régénérez les archives, puis relancez.");
        }
        return gestes.toString();
    }

    private static long compter(List<EchecUnite> refuses, CauseRefus cause) {
        return refuses.stream().filter(refus -> refus.cause() == cause).count();
    }

    /// Le verbe suit le nombre : « 1 d'entre elles ont été refusées » s'est vu à la relecture de l'aperçu.
    private static String accord(long nombre, String singulier, String pluriel) {
        return nombre == 1 ? singulier : pluriel;
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

        /// La réconciliation n'a pas pu lire (#4631). Marquée `~` et non `!` : aucune unité n'a échoué,
        /// c'est l'étape d'avant qui n'a pas tourné, et sa conséquence est que des archives déjà
        /// déposées vont repartir. Un script qui lit cette sortie doit pouvoir distinguer les deux.
        @Override
        public void reconciliationImpossible(String raison, boolean definitif) {
            sortie.println("  ~ déjà déposées : impossible à vérifier, des archives vont repartir" + " pour rien ("
                    + raison + ")" + (definitif ? "" : " Réessayez plus tard."));
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
