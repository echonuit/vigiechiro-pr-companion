package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.Severite;

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

    /// La **gravité** de ce que ce verdict dit. Portée en donnée : c'est la vue qui décide comment la
    /// rendre (couleur, icône), pas le modèle.
    ///
    /// Remplace un `boolean alerte()` qui ne distinguait pas « rassure » de « se tait » - `Concorde` et
    /// `Indisponible` rendaient tous deux `false`, donc la vue ne pouvait pas afficher une confirmation
    /// en succès. L'information était perdue avant d'arriver à l'écran (#2036, #2159).
    Severite severite();

    /// Le point tombe bien dans le carré déclaré par le site : la saisie est confirmée.
    record Concorde(String numero) implements VerdictCarre {
        @Override
        public String message() {
            return "Ce point tombe bien dans le carré " + numero + " de la grille STOC.";
        }

        @Override
        public Severite severite() {
            return Severite.SUCCES;
        }
    }

    /// Le point tombe dans un **autre** carré que celui déclaré. On ne corrige rien : le carré appartient au
    /// site, pas au point, et l'observateur peut avoir de bonnes raisons (un point en limite, un carré
    /// choisi exprès). On le **dit**, il décide.
    record Diverge(String numeroOfficiel, String numeroDeclare) implements VerdictCarre {
        @Override
        public String message() {
            return "Ce point tombe dans le carré " + numeroOfficiel + " de la grille STOC, alors que ce site"
                    + " déclare le carré " + numeroDeclare + ". Vérifiez les coordonnées, ou le n° de carré du"
                    + " site.";
        }

        @Override
        public Severite severite() {
            return Severite.AVERTISSEMENT;
        }
    }

    /// Aucun carré STOC à proximité : la position est hors de la grille (en mer, hors de France) — le plus
    /// souvent, des coordonnées inversées ou mal saisies.
    record HorsGrille() implements VerdictCarre {
        @Override
        public String message() {
            return "Aucun carré STOC ne couvre cette position. Vérifiez la latitude et la longitude.";
        }

        @Override
        public Severite severite() {
            return Severite.AVERTISSEMENT;
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

        /// Sans message, la sévérité ne sert à rien - `INFO` est le niveau qui ne dit rien de particulier.
        @Override
        public Severite severite() {
            return Severite.INFO;
        }
    }
}
