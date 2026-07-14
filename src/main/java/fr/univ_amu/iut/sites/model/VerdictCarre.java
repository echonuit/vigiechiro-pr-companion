package fr.univ_amu.iut.sites.model;

/// Ce que la **grille STOC officielle** dit d'une position, confrontée au carré que le site déclare (#733).
///
/// Le n° de carré est saisi à la main, une fois, à la création du site — et il conditionne ensuite *tout* :
/// le préfixe R6 de chaque fichier, le dossier de session, l'unité déposée sur la plateforme. Une faute de
/// frappe ne se voit qu'au dépôt, très loin en aval. Dès qu'un point d'écoute reçoit ses coordonnées, on
/// peut pourtant **demander à la plateforme** dans quel carré il tombe, et le dire.
///
/// Le verdict porte son propre message : chaque cas répond pour lui-même, plutôt qu'un `switch` chez
/// l'appelant.
public sealed interface VerdictCarre {

    /// Message à afficher, vide s'il n'y a rien à dire.
    String message();

    /// `true` si le message est une **alerte** (à afficher comme tel), `false` s'il rassure ou se tait.
    default boolean alerte() {
        return false;
    }

    /// Le point tombe bien dans le carré déclaré par le site : la saisie est confirmée.
    record Concorde(String numero) implements VerdictCarre {
        @Override
        public String message() {
            return "✓ Ce point tombe bien dans le carré " + numero + " de la grille STOC.";
        }
    }

    /// Le point tombe dans un **autre** carré que celui déclaré. On ne corrige rien : le carré appartient au
    /// site, pas au point, et l'observateur peut avoir de bonnes raisons (un point en limite, un carré
    /// choisi exprès). On le **dit**, il décide.
    record Diverge(String numeroOfficiel, String numeroDeclare) implements VerdictCarre {
        @Override
        public String message() {
            return "⚠ Ce point tombe dans le carré " + numeroOfficiel + " de la grille STOC, alors que ce site"
                    + " déclare le carré " + numeroDeclare + ". Vérifiez les coordonnées, ou le n° de carré du"
                    + " site.";
        }

        @Override
        public boolean alerte() {
            return true;
        }
    }

    /// Aucun carré STOC à proximité : la position est hors de la grille (en mer, hors de France) — le plus
    /// souvent, des coordonnées inversées ou mal saisies.
    record HorsGrille() implements VerdictCarre {
        @Override
        public String message() {
            return "⚠ Aucun carré STOC ne couvre cette position. Vérifiez la latitude et la longitude.";
        }

        @Override
        public boolean alerte() {
            return true;
        }
    }

    /// La plateforme n'a pas pu répondre (hors connexion, injoignable, refus) : on se **tait**. La saisie
    /// manuelle reste entière — c'est le repli propre exigé par l'issue, et il ne s'annonce pas, sans quoi
    /// travailler hors ligne deviendrait bruyant.
    record Indisponible() implements VerdictCarre {
        @Override
        public String message() {
            return "";
        }
    }
}
