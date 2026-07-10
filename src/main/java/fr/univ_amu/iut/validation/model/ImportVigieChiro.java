package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
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
    private final LienVigieChiroDao liens;
    private final ServiceValidation service;

    public ImportVigieChiro(ClientVigieChiro client, LienVigieChiroDao liens, ServiceValidation service) {
        this.client = Objects.requireNonNull(client, "client");
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
    /// `donnees` sur le réseau puis importe en base). Lève une [RegleMetierException] si le passage n'est
    /// rattaché à aucune participation, ou si aucun résultat n'est encore disponible (analyse Tadarida non
    /// terminée côté serveur, ou connexion indisponible).
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
            throw new RegleMetierException("Aucun résultat Tadarida disponible sur VigieChiro pour cette"
                    + " participation (analyse serveur pas encore terminée, ou connexion indisponible).");
        }
        return service.importerDepuisVigieChiro(idPassage, donnees, remplacer);
    }

    private Optional<String> participation(Long idPassage) {
        return liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
    }
}
