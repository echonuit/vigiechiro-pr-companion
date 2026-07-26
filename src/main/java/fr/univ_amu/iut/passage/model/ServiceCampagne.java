package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import java.util.List;
import java.util.Objects;

/// Service métier de la feature **campagne** (#2355) : créer, renommer et supprimer une campagne, en
/// lister. La campagne est un regroupement **facultatif** de passages ; ce service ne connaît pas les
/// passages, il gère l'objet lui-même. Le lien passage↔campagne (et le fait que supprimer une
/// campagne **détache** ses passages plutôt que de les effacer) est porté par la colonne nullable
/// `passage.campaign_id` en `ON DELETE SET NULL` (lot 1 PR 2).
///
/// Constructeur **simple** (sans annotation d'injection), assemblé par
/// [fr.univ_amu.iut.passage.di.CampagneModule]. Dates via l'[Horloge] injectée (année par défaut
/// déterministe en test).
public class ServiceCampagne {

    private final CampagneDao campagneDao;
    private final Horloge horloge;

    public ServiceCampagne(CampagneDao campagneDao, Horloge horloge) {
        this.campagneDao = Objects.requireNonNull(campagneDao, "campagneDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Crée une campagne. Le nom est obligatoire (non vide) ; l'année vaut celle de l'[Horloge] si
    /// `annee` est `null`.
    ///
    /// @return la campagne insérée, avec son `id` auto-généré
    /// @throws IllegalArgumentException si le nom est vide
    public Campagne creerCampagne(String nom, Integer annee, String commentaire) {
        String nomNettoye = exigerNom(nom);
        int anneeEffective = annee != null ? annee : horloge.aujourdhui().getYear();
        return campagneDao.insert(new Campagne(null, nomNettoye, anneeEffective, commentaire));
    }

    /// Modifie une campagne existante (renommer, changer l'année ou le commentaire).
    ///
    /// @return la campagne mise à jour
    /// @throws IllegalArgumentException si le nom est vide
    /// @throws RegleMetierException si la campagne est introuvable
    public Campagne modifierCampagne(Long idCampagne, String nom, int annee, String commentaire) {
        charger(idCampagne);
        Campagne aMettreAJour = new Campagne(idCampagne, exigerNom(nom), annee, commentaire);
        campagneDao.update(aMettreAJour);
        return aMettreAJour;
    }

    /// Supprime une campagne. Les passages qui y étaient rattachés sont **détachés** (leur
    /// `campaign_id` repasse à `null` par la clé étrangère `ON DELETE SET NULL`), jamais effacés.
    ///
    /// @throws RegleMetierException si la campagne est introuvable
    public void supprimerCampagne(Long idCampagne) {
        charger(idCampagne);
        campagneDao.delete(idCampagne);
    }

    /// Toutes les campagnes, de la plus récente à la plus ancienne.
    public List<Campagne> listerCampagnes() {
        return campagneDao.toutes();
    }

    /// Nombre de campagnes (compteur éventuel d'accueil).
    public long compterCampagnes() {
        return campagneDao.compter();
    }

    private Campagne charger(Long idCampagne) {
        Objects.requireNonNull(idCampagne, "idCampagne");
        return campagneDao
                .findById(idCampagne)
                .orElseThrow(() -> new RegleMetierException("Campagne introuvable : " + idCampagne));
    }

    private static String exigerNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom de la campagne est obligatoire.");
        }
        return nom.strip();
    }
}
