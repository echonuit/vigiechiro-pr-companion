package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import java.util.List;

/// Ce que le refus « site non rattaché » **conseille**, selon ce que la plateforme porte (#3854).
///
/// ## Pourquoi ce texte vit dans un objet à lui
///
/// Il est **lu par l'utilisateur au pire moment** - son téléversement vient d'échouer - et c'est le seul
/// endroit qui lui dise quoi faire. Or aucun aperçu ne le montrait (#3872) : les tests vérifient ce que
/// le message **dit**, jamais qu'on peut le **lire**.
///
/// Sorti de [SynchronisationParticipation], il devient **rendable** : l'aperçu appelle ce code-ci, et
/// affiche donc le texte du produit. Le recopier dans l'outil de capture aurait produit une image qui
/// dérive du jour où quelqu'un change une phrase ici - c'est le mode de panne de #1468.
public final class ConseilSiteNonRattache {

    /// L'en-tête commun : le refus se nomme avant de conseiller.
    public static final String ENTETE = "Site non rattaché à Vigie-Chiro : ";

    private ConseilSiteNonRattache() {}

    /// Deux conseils, et un seul est applicable selon ce que la plateforme porte.
    ///
    /// Un carré qui existe **sous un autre protocole** n'est pas récupérable : Companion ne gère que
    /// le Point Fixe, et conseiller de le récupérer serait un geste impossible à suivre.
    public static String selonCeQuiExiste(String numeroCarre, List<SiteVigieChiro> trouves) {
        boolean enPointFixe = trouves.stream().anyMatch(SiteVigieChiro::estPointFixe);
        if (enPointFixe) {
            return ENTETE + "le carré " + numeroCarre + " existe sur Vigie-Chiro. Ouvrez « Mes sites » ›"
                    + " « Nouveau site », saisissez ce numéro et cliquez « Récupérer ce carré » : il sera"
                    + " rattaché, avec ses points d'écoute positionnés.";
        }
        return ENTETE + "le carré " + numeroCarre + " n'existe pas en Point Fixe sur Vigie-Chiro."
                + " Activez-le sur le portail (il faut y créer un point), puis récupérez-le depuis"
                + " « Mes sites ».";
    }

    /// On n'a **pas pu** demander : ni « récupérez-le », ni « il n'existe pas ». Affirmer depuis une
    /// ignorance est le défaut que l'ADR 3458 a fermé côté verdict.
    public static String sansAvoirPuVerifier(String cause) {
        return ENTETE + "la vérification n'a pas pu se faire (" + cause + ").";
    }

    /// Le port ne connaît pas le numéro de carré (implémentation no-op, feature `sites` absente) : on
    /// garde le geste juste, sans prétendre savoir si le carré existe là-bas.
    public static String sansNumeroDeCarre() {
        return ENTETE + "récupérez ce carré depuis « Mes sites » › « Nouveau site » avant de déposer.";
    }
}
