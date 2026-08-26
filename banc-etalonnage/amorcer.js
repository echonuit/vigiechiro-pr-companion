// Amorce le banc d'étalonnage : de quoi qu'une participation existe, et rien de plus (ADR 4444).
//
// Pourquoi une insertion directe plutôt que des appels d'API : ce qu'on fabrique ici est une
// ENTRÉE, pas un observable. C'est la frontière que l'ADR 4406 a posée pour les bases de recette,
// et elle vaut ici : les sondes regardent ce que le back REND sur une écriture, pas ce que ce
// script a écrit. Passer par l'API coûterait en plus un compte administrateur, que le fournisseur
// simulé ne fabrique pas.
//
// Idempotent : on efface les documents d'amorçage avant de les reposer, pour qu'un second appel
// ne laisse pas deux jeux qui se ressemblent.
//
//   docker compose -f banc-etalonnage/compose.yml exec -T mongo \
//       mongosh --quiet vigiechiro < banc-etalonnage/amorcer.js

const AMORCE = 'banc-4444';   // marque tous les documents posés ici

// Le compte est celui que `DEV_FAKE_AUTH` fabrique au premier `/login/<service>`. Il faut donc
// avoir frappé un jeton AVANT d'amorcer, sinon il n'y a personne à qui rattacher le site.
const utilisateur = db.utilisateurs.findOne({ email: /^mock_user@/ });
if (!utilisateur) {
    print('ECHEC : aucun compte simulé. Frapper un jeton d abord :');
    print('  curl -s -i http://localhost:8080/login/google | grep -oE "token=[A-Z0-9]+" | head -1');
    quit(1);
}

['taxons', 'protocoles', 'sites', 'participations'].forEach(
    (c) => db[c].deleteMany({ amorce: AMORCE }));

const taxon = db.taxons.insertOne({
    amorce: AMORCE,
    libelle_long: 'Taxon du banc',
    libelle_court: 'Banc',
    liste_rouge: 'LC',
    _created: new Date(), _updated: new Date(), _etag: 'amorce-taxon',
}).insertedId;

const protocole = db.protocoles.insertOne({
    amorce: AMORCE,
    titre: 'Banc 4444 - Point Fixe',
    taxon: taxon,
    type_site: 'POINT_FIXE',
    macro_protocole: false,
    autojoin: true,
    _created: new Date(), _updated: new Date(), _etag: 'amorce-protocole',
}).insertedId;

// `_validate_site` exige que le site appartienne à l'appelant ET qu'il soit VERROUILLÉ : sans le
// verrou, la création d'une participation est refusée par « cannot create protocole on an unlocked
// site ». C'est le genre de détail qu'on ne devine pas, et qui coûte un tir s'il manque.
const site = db.sites.insertOne({
    amorce: AMORCE,
    titre: 'Banc 4444 - site',
    protocole: protocole,
    observateur: utilisateur._id,
    verrouille: true,
    localites: [{ nom: 'Z1' }],
    _created: new Date(), _updated: new Date(), _etag: 'amorce-site',
}).insertedId;

const participation = db.participations.insertOne({
    amorce: AMORCE,
    observateur: utilisateur._id,
    protocole: protocole,
    site: site,
    date_debut: new Date('2026-08-01T20:00:00Z'),
    _created: new Date(), _updated: new Date(), _etag: 'amorce-participation',
}).insertedId;

// Le compte doit être inscrit ET validé sur le protocole, sinon les routes qui appellent
// `ensure_protocole_joined_and_validated` refusent en 422.
db.utilisateurs.updateOne(
    { _id: utilisateur._id },
    { $set: { protocoles: [{ protocole: protocole, valide: true, date_inscription: new Date() }] } });

print('participation du banc : ' + participation);
print('site : ' + site + '  protocole : ' + protocole);
