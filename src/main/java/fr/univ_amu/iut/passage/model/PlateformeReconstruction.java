package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// Ce que la **plateforme VigieChiro** sait d'une reconstruction : la liste des participations, le détail
/// d'une nuit, et surtout la **source des observations** — le CSV téléchargé d'un coup (#1565) si la
/// plateforme l'expose, sinon la pagination `donnees` (repli). Extraite de [ServiceReconstructionPassages]
/// (plafond God Class) : toutes les lectures distantes et leur traduction en refus **motivé** (#1284)
/// vivent ici, sans aucune écriture locale.
final class PlateformeReconstruction {

    /// Nombre d'appels de détail menés **de front** (#1814). C'est une borne d'**entrée/sortie**, pas de
    /// calcul : chaque tâche est un GET qui passe son temps à attendre le réseau (fils virtuels). La borne
    /// sert donc à rester **poli** avec la plateforme, pas à occuper les cœurs.
    private static final int DETAILS_DE_FRONT = 8;

    private final ClientVigieChiro client;

    /// Fan-out borné des appels de détail (moteur de #1779) : la synchro en demande **un par nuit
    /// nouvelle**, et les mener en série ferait attendre autant d'allers-retours réseau qu'il y a de nuits.
    private final ExecutionParallele detailsEnParallele = new ExecutionParallele(DETAILS_DE_FRONT);

    PlateformeReconstruction(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Toutes les participations du compte (`GET /moi/participations`), ou un refus motivé.
    List<ParticipationVigieChiro> participations() {
        return exiger(client.mesParticipations(), "la liste de vos participations");
    }

    /// Résumé d'une participation par son `_id` (le carré et la localité, absents du détail par id, s'y
    /// lisent), retrouvé dans la liste du compte.
    ParticipationVigieChiro resume(String idParticipation) {
        return participations().stream()
                .filter(participation -> idParticipation.equals(participation.id()))
                .findFirst()
                .orElseThrow(() -> new RegleMetierException(
                        "Participation introuvable parmi celles de votre compte : " + idParticipation + "."));
    }

    /// Détail d'une participation (`GET /participations/#id` : dates, configuration matérielle).
    ParticipationDetail detail(String idParticipation) {
        return exiger(client.participation(idParticipation), "le détail de cette participation");
    }

    /// Le détail de **plusieurs** participations, rendu **dans le même ordre**, chacun **best-effort** : une
    /// nuit dont le détail est indisponible (injoignable, refus) rend un `Optional` vide au lieu de faire
    /// échouer le lot. C'est ce dont la synchro a besoin (#1814) pour rapatrier l'identité de chaque nuit
    /// nouvelle sans qu'un accroc sur l'une écarte les autres.
    List<Optional<ParticipationDetail>> detailsBestEffort(List<String> idsParticipation) {
        return detailsEnParallele.cartographier(
                idsParticipation, "Détails", this::detailSiDisponible, progres -> {}, JetonAnnulation.neutre());
    }

    /// Le détail d'une participation, ou **vide** s'il est indisponible : un détail manquant ne doit ni
    /// écarter la nuit ni casser le lot.
    private Optional<ParticipationDetail> detailSiDisponible(String idParticipation) {
        try {
            return Optional.of(detail(idParticipation));
        } catch (RuntimeException detailIndisponible) {
            return Optional.empty();
        }
    }

    /// La **source des observations** : le CSV téléchargé d'un coup (#1565) si la plateforme l'expose,
    /// sinon la pagination `donnees` (repli, plus lent). Dans les deux cas, elle porte les noms de fichiers
    /// (pour recréer les séquences), le nombre d'observations (pour le rapport) et le geste d'import. C'est
    /// l'étape réseau : progression et annulation y sont honorées.
    ObservationsAReconstruire observations(
            String idParticipation,
            ImportObservations importateur,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        jeton.leverSiAnnule();
        progres.accept(new Progression("Téléchargement des observations…", 0.10));
        Optional<String> csv =
                exiger(client.csvObservations(idParticipation), "le CSV d'observations de cette participation");
        if (csv.isPresent()) {
            String contenu = csv.get();
            List<String> noms = importateur.nomsSequencesCsv(contenu);
            if (!noms.isEmpty()) {
                return new ObservationsAReconstruire(
                        noms,
                        nbLignesObservations(contenu),
                        idPassage -> importateur.importerCsv(idPassage, contenu, false));
            }
        }

        // Repli : le CSV n'est pas (encore) exposé - on rapatrie les donnees page par page (des dizaines de
        // pages, d'où le suivi PAGE PAR PAGE et l'annulation, sans quoi la barre restait figée plusieurs
        // minutes).
        jeton.leverSiAnnule();
        List<DonneeVigieChiro> donnees = donnees(idParticipation, (page, totalPages) -> {
            jeton.leverSiAnnule();
            progres.accept(ProgressionTelechargement.pour(page, totalPages));
        });
        if (donnees.isEmpty()) {
            throw new RegleMetierException("VigieChiro ne renvoie aucune donnée pour cette participation :"
                    + " l'analyse n'est probablement pas terminée. Réessayez plus tard.");
        }
        List<String> noms = donnees.stream().map(DonneeVigieChiro::titre).toList();
        int nb = donnees.stream()
                .mapToInt(donnee -> donnee.observations().size())
                .sum();
        return new ObservationsAReconstruire(noms, nb, idPassage -> importateur.importer(idPassage, donnees, false));
    }

    private List<DonneeVigieChiro> donnees(String idParticipation, SuiviPagination suivi) {
        return exiger(client.donnees(idParticipation, suivi), "les observations de cette participation");
    }

    /// Nombre de lignes de données d'un CSV (hors entête, hors lignes vides) : le compte d'observations
    /// rapporté à l'utilisateur, obtenu sans re-parser le CSV (le parsing fin du format Tadarida reste
    /// dans `validation`, derrière le port).
    private static int nbLignesObservations(String contenuCsv) {
        return (int)
                contenuCsv.lines().skip(1).filter(ligne -> !ligne.isBlank()).count();
    }

    /// Traduction **unique** d'une issue d'appel (#1284) en valeur ou en refus **motivé** : une seule
    /// formulation par cause (non connecté, injoignable, refusé), et le geste qui va avec — au lieu de
    /// répéter le même `switch` à chaque lecture distante.
    ///
    /// @param quoi ce qu'on lisait, pour que le message dise **ce qui** a échoué
    private static <T> T exiger(ReponseApi<T> reponse, String quoi) {
        return switch (reponse) {
            case ReponseApi.Succes<T>(T valeur) -> valeur;
            case ReponseApi.NonConnecte<T> ignore ->
                throw new RegleMetierException("Non connecté à VigieChiro : collez un jeton (menu ☰ >"
                        + " Se connecter à VigieChiro) avant de reconstruire un passage.");
            case ReponseApi.Injoignable<T>(String cause) ->
                throw new RegleMetierException(
                        "VigieChiro est injoignable (" + cause + ") : " + quoi + " n'a pas pu être lu.");
            case ReponseApi.Refuse<T>(int statut, String corps) ->
                throw new RegleMetierException("VigieChiro a refusé de rendre " + quoi + " (HTTP " + statut + " : "
                        + corps + "). C'est probablement un défaut de l'application : signalez-le.");
        };
    }
}
