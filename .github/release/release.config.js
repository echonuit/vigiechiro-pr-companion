// Configuration d'ANALYSE : elle calcule la prochaine version et ses notes, et n'écrit rien.
//
// Pourquoi elle existe (#2738, lot 3 du chantier #2720) : la chaîne de publication ne se vérifiait
// nulle part avant de publier. `release.yml` ne se déclenche qu'au push sur `main`, donc aucune CI de
// PR ne la traversait : une erreur d'installation ou de configuration ne se serait vue qu'à la
// prochaine release, c'est-à-dire en production.
//
// Elle DÉRIVE de `.releaserc.json` au lieu de recopier ses options : les `parserOpts` du dépôt (qui
// tolèrent l'espace avant les deux-points, « fix(ci) : sujet », usage typographique français) ne
// doivent exister qu'à un seul endroit. Une copie divergerait, et la version calculée en vérification
// ne serait plus celle que la publication calculera.
//
// Elle ne garde que les deux greffons de CALCUL. Les trois autres (`changelog`, `github`, `git`)
// écrivent : les inclure exigerait un jeton en écriture pour une simple vérification, et ferait courir
// le risque qu'une vérification publie.
//
// Utilisée en lançant semantic-release DEPUIS ce dossier : cosmiconfig remonte l'arborescence et
// trouve ce fichier avant `.releaserc.json`. Les commandes git portent quand même sur tout le dépôt,
// ce dossier en faisant partie.

const base = require('../../.releaserc.json');

/// Les greffons qui se contentent de lire l'historique et d'en déduire une version.
const CALCUL = ['@semantic-release/commit-analyzer', '@semantic-release/release-notes-generator'];

const nomDe = (greffon) => (Array.isArray(greffon) ? greffon[0] : greffon);

module.exports = {
  ...base,
  plugins: base.plugins.filter((greffon) => CALCUL.includes(nomDe(greffon))),
};
