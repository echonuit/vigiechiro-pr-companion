package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// **Import des résultats Tadarida depuis VigieChiro** (axe 4.2) : pour un passage rattaché à une
/// participation (lien posé au dépôt, `ENTITE_PASSAGE`, #142), récupère les `donnees` de cette
/// participation via le client HTTP et les importe via [ServiceValidation#importerDepuisVigieChiro].
///
/// Orchestration réseau + import, **sans IHM** (règle ArchUnit `model_sans_javafx`). **Bloquant** (réseau)
/// : à appeler hors du fil JavaFX. Le couplage à [ClientVigieChiro] est confiné ici ; ce service est
/// injecté de façon **optionnelle** dans la feature `audio` (absent des injecteurs de capture assemblés
/// sans `connexion`, donc sans client HTTP).
public class ImportVigieChiro {

    private final ClientVigieChiro client;

    /// État du traitement serveur (#1260) : ce qui permet de dire POURQUOI il n y a rien a importer.
    private final TraitementVigieChiro traitement;

    private final LienVigieChiroDao liens;
    private final ServiceValidation service;

    public ImportVigieChiro(
            ClientVigieChiro client,
            TraitementVigieChiro traitement,
            LienVigieChiroDao liens,
            ServiceValidation service) {
        this.client = Objects.requireNonNull(client, "client");
        this.traitement = Objects.requireNonNull(traitement, "traitement");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.service = Objects.requireNonNull(service, "service");
    }

    /// `true` si le passage est **rattaché** à une participation VigieChiro (donc importable sans saisie).
    public boolean estRattache(Long idPassage) {
        return participation(idPassage).isPresent();
    }

    /// **Participations** de l'observateur (`GET /moi/participations`), pour rattacher à la main un passage
    /// à une participation existante (nuit non déposée par l'app). Liste vide si non connecté / indisponible.
    public List<ParticipationVigieChiro> participationsDisponibles() {
        return client.mesParticipations();
    }

    /// **Rattache** le passage à une participation VigieChiro (stocke le lien `ENTITE_PASSAGE`) : l'import
    /// de ses résultats devient alors possible. Idempotent (upsert : un seul lien par passage).
    public void rattacher(Long idPassage, String participationId) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(participationId, "participationId");
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), participationId));
    }

    /// Importe les résultats Tadarida de la participation rattachée au passage. **Bloquant** (récupère les
    /// `donnees` sur le réseau puis importe en base).
    ///
    /// Lève une [RegleMetierException] si le passage n'est rattaché à aucune participation, ou s'il n'y a
    /// rien à importer — et, dans ce dernier cas, **dit pourquoi** (#1264) : le serveur renvoie « 200, liste
    /// vide » tant que l'analyse n'est pas terminée, si bien que l'absence de résultats n'est pas une erreur
    /// mais un **état**, qu'on relit pour l'expliquer (cf. [#pourquoiRienAImporter]).
    ///
    /// @param idPassage passage cible
    /// @param remplacer remplace le jeu existant (en préservant les validations observateur) si `true`
    /// @return le bilan de l'import
    public BilanImport importer(Long idPassage, boolean remplacer) {
        Objects.requireNonNull(idPassage, "idPassage");
        String participationId = participation(idPassage)
                .orElseThrow(() -> new RegleMetierException("Ce passage n'est rattaché à aucune participation"
                        + " VigieChiro. Déposez-le d'abord sur VigieChiro (ou rattachez-le à une participation"
                        + " existante)."));
        List<DonneeVigieChiro> donnees = client.donnees(participationId);
        if (donnees.isEmpty()) {
            throw new RegleMetierException(pourquoiRienAImporter(participationId));
        }
        return service.importerDepuisVigieChiro(idPassage, donnees, remplacer);
    }

    /// **Pourquoi il n'y a rien à importer** (#1264). Le serveur répond « 200, liste vide » tant que
    /// l'analyse n'est pas finie : l'absence de résultats n'est donc pas une erreur, c'est un état — encore
    /// faut-il le dire. Le message d'avant les confondait tous (« analyse pas encore terminée, ou connexion
    /// indisponible »), laissant l'observateur sans rien à faire de cette information.
    ///
    /// On relit donc l'état du traitement (#1260) et on rend la vraie raison, avec le geste qui va avec.
    private String pourquoiRienAImporter(String participationId) {
        Traitement traitement = this.traitement.etat(participationId);
        if (traitement.estInconnu()) {
            return "L'analyse n'a jamais été lancée sur VigieChiro pour cette nuit (ou la plateforme est"
                    + " injoignable). Lancez-la depuis « Préparer le dépôt », étape ④.";
        }
        return switch (traitement.etat()) {
            case PLANIFIE ->
                "L'analyse est planifiée sur VigieChiro, mais n'a pas encore démarré."
                        + " Réessayez une fois qu'elle sera terminée (le suivi est affiché dans « Préparer le dépôt »).";
            case EN_COURS, RETRY ->
                "L'analyse est en cours sur VigieChiro : les observations n'existeront"
                        + " qu'une fois le calcul terminé. Comptez plusieurs dizaines de minutes.";
            case ERREUR ->
                "L'analyse a échoué sur VigieChiro : il n'y a aucune observation à importer." + motif(traitement);
            case FINI ->
                "VigieChiro annonce l'analyse terminée, mais ne renvoie aucune observation pour cette"
                        + " participation. Vérifiez le dépôt (les fichiers sont-ils bien arrivés ?).";
        };
    }

    /// Motif de l'échec, rendu par le [Traitement] lui-même (une seule extraction pour toute l'application).
    private static String motif(Traitement traitement) {
        return traitement.motifCourt().map(ligne -> " Motif : " + ligne).orElse("");
    }

    private Optional<String> participation(Long idPassage) {
        return liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
    }
}
