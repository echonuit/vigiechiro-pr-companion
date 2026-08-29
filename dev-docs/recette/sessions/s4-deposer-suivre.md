# S4 · Déposer et suivre

> **Écran propriétaire** : lot (préparer, générer, déposer, déposé, alertes).
> **Features** : lot, depot-vigiechiro, synchronisation-participation. · **Statut : à jouer** (passe
> statique prête ; le script sera annoté après la séance).
> Retour à la [méthode](../index.md).

## Objectif

Déposer une nuit vérifiée : le lot en quatre temps, le dépôt **réel** sur la plateforme, « Lancer la
participation », puis le suivi du traitement. S4 est la première session qui **écrit sur le serveur**.

!!! danger "Ce qu'un dépôt écrit, et que l'application ne peut pas défaire"
    Sur un carré **relié** (130711), un dépôt écrit, dans l'ordre : `POST …/participations` (participation),
    puis par archive `POST /fichiers` + `PUT` S3 signé + `POST /fichiers/{id}` (5 en parallèle), puis au
    clic « Lancer la participation » `POST …/compute` (calcul Tadarida national). **Aucun `DELETE`** n'existe
    côté client : « Réinitialiser le dépôt » et « Annuler le dépôt » sont **100 % locaux**. Le nettoyage
    éventuel se fait **à la main sur le portail**. Piège : la participation naît **dès l'import** sur un site
    relié (`ServiceImport.creerParticipationSiPossible`), pas au dépôt.

## Décision arbitrée : dépôt réel sur données réelles

- **Données** : enregistreur **PR1997632**, carte SD réelle, **nuit du 05/07 (1623 wav, 11 Go)** non
  encore déposée : c'est une donnée réelle qui va à sa vraie place (le calcul devient l'usage nominal,
  pas une pollution), et enfin le vrai test de volume (#26/#27).
- **Carré cible : 130711** (le vrai carré où PR1997632 était posé, déjà relié).
- **Mode : IHM en ZIP** (c'est l'écran Lot qu'on recette : parallélisme, reprise).
- **Workspace : le workspace habituel (production)**, pas celui de recette.
- **Amenée de la nuit : par réactivation** (#1302) : la nuit existe en production comme passage archivé
  (audio purgé) ; le réimport depuis la carte SD la réactive (empreinte vérifiée). **Bonus : S4 recette
  donc aussi la réactivation en vrai.**
- **Délai Tadarida réel ≈ 2 h** (pas 24-48 h) : lancer le calcul **tôt**, dérouler le bloc A pendant qu'il
  tourne, et enchaîner **S5 le jour même** sur les vrais résultats.

!!! bug "Bug bloquant déjà tracé, à filmer en séance : #1514"
    Un passage **déposé peut régresser vers Vérifié** depuis l'écran de vérification (carte active, aucune
    garde de statut, `ServiceQualification` écrit `VERIFIE` en contournant le workflow, `deposeLe`
    conservé). La CLI, elle, refuse. À filmer dans le bloc A sur un passage marqué déposé manuellement.

**Sémantique de « lot » (tranchée)** : un lot = un passage = une nuit (`Lot` porte un unique
`idPassage`). « À jeter » ne retire pas un morceau d'un panier : il **rejette la nuit entière**.

## Le script (une case = un fait observable)

**Bloc A · Local, zéro écriture serveur (carré 640380, passages existants)**

*A1 · Préparer (passage n° 1, Vérifié / OK)*

- **S4-01** · *geste: lire-le-stepper-du-depot* · Le stepper affiche 4 temps : « 1 · Préparer », « 2 · Générer les archives », « 3 · Téléverser »,
  « 4 · Marquer déposé ».
- **S4-02** · *geste: lire-le-stepper-du-depot* · L'étape courante est ① sur un passage Vérifié.
- **S4-03** · *geste: lire-la-checklist-de-coherence* · La checklist de cohérence affiche une ligne par contrôle, même satisfait (✓ / ✗ / ⚠).
- **S4-04** · *geste: lire-la-checklist-de-coherence* · Un ⚠ (relevé climatique absent) n'empêche pas de préparer.
- **S4-05** · *geste: preparer-le-lot* · « Vérifier et préparer le lot » → statut « Prêt à déposer », étape courante ②.
- **S4-06** · *geste: un-verdict-a-jeter-refuse-la-preparation* · Sur un passage au verdict « À jeter » : la préparation est refusée, message qui nomme le passage.
- **S4-07** · *geste: preparer-le-lot* · « Préparer » reste actif même avec un contrôle en ✗ (la fiche dit grisé, le code dit actif : S4-C05).

*A2 · Générer les archives*

- **S4-08** · *geste: generer-les-archives* · La génération affiche une barre de progression déterminée.
- **S4-09** · *geste: generer-les-archives* · Le libellé « Compression X/N » donne une estimation du temps restant.
- **S4-10** · *geste: generer-les-archives* · Les actions sont neutralisées pendant la génération.
- **S4-11** · *geste: generer-les-archives* · Le tableau des archives permet de choisir/réordonner ses colonnes.
- **S4-12** · *geste: regler-le-plafond-d-archive* · Réglages ▸ Dépôt : le plafond d'archive (700 Mo par défaut) est réglable entre 50 et 700 Mo.
- **S4-13** · *geste: regler-le-plafond-d-archive* · Le nouveau plafond s'applique à la génération suivante sans redémarrage.
- **S4-14** · *geste: le-garde-fou-disque-avant-de-generer* · Garde-fou disque : bandeau rouge et « Générer » désactivé **avant** le clic si l'espace est
  insuffisant.

*A3 · Téléverser (refus « site non relié »)*

- **S4-15** · *geste: un-site-non-rattache-refuse-le-televersement* · « ☁ Téléverser sur Vigie-Chiro » est présent (l'application est connectée).
- **S4-16** · *geste: un-site-non-rattache-refuse-le-televersement* · Le clic échoue proprement avec « Site non rattaché à Vigie-Chiro… » (640380) : **rien** n'a été écrit.
  Le message **nomme le geste à faire** pour ce carré-là (#3854) : « Récupérer ce carré » si le carré
  existe en Point Fixe sur la plateforme, sinon l'activer sur le portail. Il ne dit **jamais**
  « synchronisez vos sites », qui ne ramène pas un carré sans nuit déposée.
- **S4-17** · *geste: un-site-non-rattache-refuse-le-televersement* · Rien, avant ce clic, n'annonçait le dépôt impossible sur ce site (S4-C01 : le garde-fou arrive après
  la génération).
- **S4-18** · *geste: le-dossier-de-depot-et-son-chemin* · « 📂 Ouvrir le dossier (dépôt manuel) » ouvre le dossier `depot/`.
- **S4-19** · *geste: le-dossier-de-depot-et-son-chemin* · Ce bouton est grisé sans archives, avec une infobulle explicative.
- **S4-20** · *geste: le-dossier-de-depot-et-son-chemin* · Le chemin du dossier de dépôt porte, **à côté de lui**, un bouton « Copier » ; le cliquer place ce
  chemin dans le presse-papier, et ce qui s'y trouve est **exactement** ce que l'écran affiche (le
  coller dans un éditeur pour le lire).
- **S4-21** · *geste: le-dossier-de-depot-et-son-chemin* · Sur une nuit introuvable (chemin vide), ce bouton est **grisé** comme ses voisins, avec son
  infobulle : il n'écrase pas le presse-papier avec du vide.

*A4 · Marquer déposé, réinitialiser, régression*

- **S4-22** · *geste: marquer-depose-a-la-main* · Sans participation liée, le bouton est « ✅ Marquer déposé ».
- **S4-23** · *geste: marquer-depose-a-la-main* · Après « Marquer déposé » : statut « Déposé », et **toutes** les étapes du stepper sont franchies
  (S4-C03 : y compris « Lancer la participation », qui n'a pas eu lieu).
- **S4-24** · *geste: marquer-depose-a-la-main* · Sur le passage déposé, la carte « Sons & validation » se déverrouille.
- **S4-25** · *geste: reinitialiser-le-depot* · « 🔄 Réinitialiser le dépôt » est visible, avec son infobulle.
- **S4-26** · *geste: reinitialiser-le-depot* · Après réinitialisation : table vidée, statut « Prêt à déposer », message explicite.
- **S4-27** · *geste: marquer-depose-a-la-main* · **#1514 à filmer** : sur le passage déposé, la carte « Vérifier l'enregistrement » est-elle active ?
  Poser un nouveau verdict : le passage régresse-t-il en « Vérifié » en gardant sa date de dépôt ?
- **S4-28** · *geste: marquer-depose-a-la-main* · « ↩ Annuler le dépôt » (M-Passage) : visible sur un passage déposé, avec confirmation.
- **S4-29** · *geste: un-passage-archive-garde-ses-archives* · Passage archivé : les archives survivent-elles à la purge ?

*A5 · Refus définitif et réarmement (#3687, #3688, #3689, #3962)*

> **Fixture.** Lancer le stub avec `VIGIECHIRO_STUB_REFUS=403`, puis l'application avec
> `VIGIECHIRO_URL` pointée dessus (recette : `dev-docs/recette/fixtures.md`, § « Provoquer un refus de
> dépôt »). Le stub ne refuse que `/fichiers` et `/multipart` : on peut donc se connecter et atteindre
> le dépôt normalement, et **c'est le point** - le cas à jouer est ce qui se passe *après* le refus.


- **S4-30** · *geste: un-depot-refuse-par-le-serveur* · Téléverser avec le stub en 403 : **chaque** archive part en échec, et la table donne la cause de
  chacune au survol (« HTTP 403 »).
- **S4-31** · *geste: un-depot-refuse-par-le-serveur* · Le bouton de téléversement **cesse de s'appeler « Reprendre le dépôt »** et redevient « Téléverser
  sur Vigie-Chiro » : il n'y a plus rien à reprendre, et il ne le promet plus.
- **S4-32** · *geste: un-depot-refuse-par-le-serveur* · Le compte rendu s'intitule « **Dépôt incomplet** », et non « Nuit déposée sur Vigie-Chiro ».
- **S4-33** · *perceptif* · Il dit le nombre d'archives refusées, **et** conseille la reconnexion -
  parce que ce refus-là tient aux droits. C'est la **lisibilité** de la phrase qu'on juge, pas sa
  présence : une assertion la trancherait mal.
- **S4-34** · *geste: reprendre-un-depot-apres-un-refus* · Arrêter le refus (`VIGIECHIRO_STUB_REFUS=0`, stub relancé), puis **se reconnecter** dans
  l'application : les unités refusées redeviennent reprenables, et le bouton se réintitule
  « Reprendre le dépôt » **sans autre geste**.
- **S4-35** · *geste: reprendre-un-depot-apres-un-refus* · Reprendre le dépôt : seules les archives refusées repartent, jamais celles déjà en ligne.
- **S4-36** · *geste: un-refus-422-ne-conseille-pas-la-reconnexion* · Rejouer S4-30 à S4-33 avec `VIGIECHIRO_STUB_REFUS=422` : le compte rendu **ne conseille pas** la
  reconnexion, parce qu'un contenu refusé ne se répare pas ainsi. Une reconnexion ne réarme rien.

**Bloc B · Dépôt réel (130711, nuit du 05/07, ZIP, calcul lancé)**

- **S4-37** · *prérequis: une base de départ où la nuit est un passage archivé, audio purgé, que la carte SD réactive (générateur de bases déclarées de #4325)* · Réactivation : réimport de la nuit depuis la carte SD → empreinte vérifiée → passage réactivé.
- **S4-38** · Qualification : verdict OK posé.
- **S4-39** · Réglages ▸ Dépôt : plafond d'archive abaissé (~50 Mo) pour obtenir plusieurs archives.
- **S4-40** · *hors-portée: un serveur qui accepte réellement des archives. Ce que le cas observe est la table qui se remplit ligne à ligne, donc le transfert et non son résultat* · La table de dépôt affiche une ligne par archive (en attente → en cours → déposé).
- **S4-41** · *hors-portée: cinq transferts qui progressent VRAIMENT de front. Sur un lien local on verrait cinq tâches planifiées, ce qui n'est pas la même chose* · Cinq lignes sont « en cours » simultanément (parallélisme de 5).
- **S4-42** · *hors-portée: des octets qui partent sur un vrai lien. Sur un lien local, la barre mesurerait la remise à un tampon et non un envoi* · Une barre de progression par archive reflète les octets envoyés.
- **S4-43** · *hors-portée: une manipulation du réseau en cours d'opération - coupure, bridage à quelques dizaines de ko/s - que le banc ne sait pas provoquer sans mentir sur la cause* · Couper le réseau pendant le dépôt : échecs avec raison au survol, le bouton devient « ↻ Reprendre le
  dépôt ».
- **S4-44** · *hors-portée: un serveur qui a réellement gardé une partie des archives, et qui dit lesquelles : la reprise se juge sur ce qu'il en reste chez LUI* · Reprendre ne renvoie que les archives manquantes.
- **S4-45** · *prérequis: une base de départ où un dépôt est en cours, une partie des unités déposées et le reste non (générateur de bases déclarées de #4325)* · Fermer puis rouvrir l'écran : la table se réhydrate.
- **S4-46** · *hors-portée: toutes les unités réellement déposées, donc autant de transferts menés à bout* · Le passage ne devient « Déposé » que lorsque **toutes** les unités le sont.
- **S4-47** · *hors-portée: la plateforme qui accepte un calcul et rend son état. « Analyse planifiée » est SA réponse, pas la nôtre* · « 🚀 Lancer la participation » : la carte « Traitement Vigie-Chiro » apparaît (« Analyse planifiée »).
- **S4-48** · *hors-portée: un état de traitement qui bouge côté serveur entre deux relevés, sans quoi « Actualiser » ne relève rien* · « 🔄 Actualiser » relève l'état, **sans polling** automatique.
- **S4-49** · *hors-portée: une manipulation du réseau en cours d'opération - coupure, bridage à quelques dizaines de ko/s - que le banc ne sait pas provoquer sans mentir sur la cause* · Hors connexion, « Actualiser » dit « Impossible de joindre Vigie-Chiro » **sans effacer** le dernier
  état connu.
- **S4-50** · *prérequis: une base de départ portant un état de traitement déjà relevé, avec sa date (générateur de bases déclarées de #4325)* · Fermer/rouvrir l'application : le dernier état connu est réaffiché avec sa date, sans réseau.
- **S4-51** · *hors-portée: rien à observer. C'est une consigne à l'opérateur pour la suite, pas un fait d'écran* · **Noter l'identifiant de la participation** (nettoyage manuel éventuel + matériau de S5).

### Traitement en lot : ce qu'un seul poste ne peut pas prouver (#2357)

> Ces cases ne sont **pas automatisables**. Les tests éprouvent le lot avec des actions doublées, sur
> une base en mémoire : ils prouvent l'ordonnancement, jamais qu'une vraie rafale reste **polie** avec
> la plateforme, ni qu'un lot interrompu au milieu d'un vrai téléversement se **reprend**. Ce sont
> précisément les deux propriétés qui ont motivé la conception séquentielle
> ([ADR 2357](../../decisions/2357-un-traitement-en-lot-compose-des-gestes-unitaires.md)).

*Bloc E · Un lot de trois nuits, pour de vrai*

- **S4-52** · *geste: cocher-des-nuits-et-lire-le-menu-de-lot* · Cocher trois nuits (Ctrl+clic) : les quatre entrées de lot du menu principal (☰) s'activent et annoncent
  « des 3 lignes cochées ».
- **S4-53** · *geste: ce-que-l-annonce-d-un-lot-chiffre* · « Préparer le dépôt des 3 lignes cochées… » : l'annonce dit **3 sur 3**, aucune écartée.
- **S4-54** · *geste: ce-que-l-annonce-d-un-lot-chiffre* · Rejouer la même action : l'annonce dit **0 sur 3**, les trois écartées « dépôt déjà préparé », et
  « Rien ne sera fait » ; renoncer ne touche rien.
- **S4-55** · *geste: ce-que-l-annonce-d-un-lot-chiffre* · Cocher une nuit déjà déposée avec deux nuits prêtes : l'annonce **nomme** la déposée et son motif.
- **S4-56** · *hors-portée: ce qu'un seul poste ne peut pas prouver, comme le titre de la section le dit : un débit observé, un parallélisme réel, une reprise entre deux machines* · « Téléverser les 3 lignes cochées… » : le débit observé reste celui d'**une** nuit (5 unités de
  front, pas 15). À mesurer côté réseau, c'est le seul endroit où cela se voit.
- **S4-57** · *hors-portée: ce qu'un seul poste ne peut pas prouver, comme le titre de la section le dit : un débit observé, un parallélisme réel, une reprise entre deux machines* · **Annuler** en cours de téléversement de la deuxième nuit : le lot s'arrête, la deuxième reste en
  « Dépôt en cours », la troisième est **intacte** (statut inchangé).
- **S4-58** · *hors-portée: ce qu'un seul poste ne peut pas prouver, comme le titre de la section le dit : un débit observé, un parallélisme réel, une reprise entre deux machines* · Relancer le même lot : la deuxième **reprend** là où elle s'était arrêtée, la troisième part
  normalement, aucune archive n'est renvoyée deux fois.
- **S4-59** · *geste: le-compte-rendu-d-un-lot* · Le compte rendu final donne **une ligne par nuit**, dans l'ordre de la sélection.
- **S4-60** · *hors-portée: une manipulation du réseau en cours d'opération - coupure, bridage à quelques dizaines de ko/s - que le banc ne sait pas provoquer sans mentir sur la cause* · **Débrancher le réseau** avant « Importer les résultats des 3 lignes cochées… » : les trois
  remontent en échec avec un motif qui dit quoi faire (« Connectez-vous depuis le menu principal (☰) … »), et le
  lot va au bout au lieu de s'arrêter à la première.
- **S4-61** · *hors-portée: une manipulation du réseau en cours d'opération - coupure, bridage à quelques dizaines de ko/s - que le banc ne sait pas provoquer sans mentir sur la cause* · **Réseau très dégradé** (bridage à quelques dizaines de ko/s, ou coupures brèves répétées), pour
  provoquer de vraies **temporisations de reprise** : pendant l'attente affichée « nouvelle tentative
  dans N s », cliquer **Annuler**. La main doit revenir **sans attendre la fin du décompte**.

  C'est le seul endroit où cette propriété s'observe. Les tests injectent un temporisateur factice
  qui n'attend pas : ils prouvent que l'attente est **découpée** et que le renoncement est **lu**
  entre deux tranches, jamais que la latence ressentie sur un vrai réseau reste acceptable
  (#2686, tranches de 200 ms).
- **S4-62** · *geste: declencher-le-calcul-sur-une-nuit-deja-analysee* · « Déclencher le calcul » sur une nuit déjà analysée : elle ressort en **échec** avec « relancer
  effacerait les observations », jamais recalculée.
- **S4-63** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · Les mêmes gestes en ligne de commande : `vigiechiro traiter-passages --action televerser --passage
  … --json` rend le même verdict par passage, et un code de sortie non nul si l'un a échoué.

*Bloc F · Les deux mécanismes de lot, l'un après l'autre (#2755)*

> Ces trois cases existent parce qu'une affirmation du bilan de l'EPIC #2349 n'est **pas** vérifiée : que
> les deux mécanismes de traitement groupé cohabitent sans se gêner. Ils sont dans le **même menu principal (☰)**, à
> quatre entrées d'intervalle, et rafraîchissent la **même table** par deux chemins qui ne se connaissent
> pas. Aucun test ne les joue à la suite, et aucun ne le peut aujourd'hui : cliquer une entrée de lot
> ouvre une vraie fenêtre de progression, que `TraitementLot` n'expose pas aux tests.

- **S4-64** · *geste: completer-des-nuits-puis-enchainer-un-lot* · « Compléter une nuit récupérée… » sur deux nuits, puis **fermer la modale** : les deux nuits
  complétées apparaissent dans le tableau de « Carte & passages », avec leur statut réel.
- **S4-65** · *geste: completer-des-nuits-puis-enchainer-un-lot* · Enchaîner **sans quitter l'écran** : cocher ces deux nuits, « Préparer le dépôt des 2 lignes
  cochées… ». L'annonce les compte comme **éligibles** (et non « déjà déposé » ni « pas encore
  vérifié ») - c'est-à-dire que le tableau qu'on vient de rafraîchir dit la vérité.
- **S4-66** · *geste: completer-des-nuits-puis-enchainer-un-lot* · L'inverse : lancer un lot, puis rouvrir « Compléter une nuit récupérée… » **sans quitter l'écran**.
  La liste des nuits à compléter est celle d'après le lot, pas celle d'avant.

### Métadonnées : ce que la plateforme affiche vraiment (#1828, #1844, #1845)

> Ces cases ne sont **pas automatisables** : elles se jouent sur la **fiche web** de la participation,
> seul juge de ce qui est arrivé. Trois défauts de ce chantier ont tous **réussi silencieusement** -
> l'application annonçait « envoyées » et la plateforme n'affichait rien (voir
> [ADR 0020](../../decisions/0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md)). Un code de
> retour vert ne prouve donc rien ici. Préparer la fiche **avant** : depuis le formulaire web, renseigner
> `micro0_numero_serie` et un canal, pour pouvoir vérifier ensuite qu'ils ont survécu.

- **S4-67** · *geste: saisir-l-enregistreur-d-un-passage* · Modale « Modifier le passage » : le champ **Enregistreur** propose le n° de série lu dans les noms de
  fichiers de la nuit (`LogPR…` / `PaRecPR…`).
- **S4-68** · *geste: saisir-l-enregistreur-d-un-passage* · Saisir « INCONNU » est **refusé** (ce n'est pas une valeur, c'est un aveu d'ignorance).
- **S4-69** · *geste: envoyer-les-metadonnees-et-lire-le-compte-rendu* · « Envoyer vers Vigie-Chiro » affiche un compte rendu, succès **comme** échec.
- **S4-70** · *hors-portée: la fiche WEB de la plateforme, dans un navigateur. Aucun carton n'y peut rien : il remplace une ÉTAPE muette, pas l'observable lui-même* · Sur la **fiche web** rechargée : le n° de série **apparaît** dans le champ du formulaire (et pas
  seulement dans le JSON) - c'est le défaut de clé de #1844.
- **S4-71** · *hors-portée: la fiche WEB de la plateforme, dans un navigateur. Aucun carton n'y peut rien : il remplace une ÉTAPE muette, pas l'observable lui-même* · Sur la **fiche web** : les **températures** de début et de fin de nuit apparaissent.
- **S4-72** · *hors-portée: la fiche WEB de la plateforme, dans un navigateur. Aucun carton n'y peut rien : il remplace une ÉTAPE muette, pas l'observable lui-même* · Sur la **fiche web** : `micro0_numero_serie` et le canal renseignés au préalable sont **toujours là**
  (l'envoi n'efface pas ce que l'application ne modélise pas).
- **S4-73** · *geste: saisir-l-enregistreur-d-un-passage* · Sur une nuit dont l'enregistreur est inconnu, un envoi **ne publie pas** « INCONNU » : le champ reste
  vide sur la fiche web.
- **S4-74** · *geste: envoyer-les-metadonnees-hors-connexion* · Couper le réseau, « Envoyer » : la modale **reste ouverte**, la cause est à l'écran, et un second
  essai une fois le réseau revenu aboutit.
- **S4-75** · *hors-portée: un fichier de journal à ouvrir hors de l'application* · `logs/vigiechiro-0.log` porte une ligne par échange (méthode, chemin, issue, durée).
- **S4-76** · *hors-portée: un fichier de journal à ouvrir hors de l'application* · Un refus serveur y figure **avec le corps de la réponse** (la cause, pas seulement le statut).
- **S4-77** · *hors-portée: un fichier de journal à ouvrir hors de l'application* · **Ouvrir le journal et y chercher le jeton** : il n'y figure ni en clair, ni encodé, ni via une URL S3
  signée (le journal doit pouvoir être joint à un signalement).

**Bloc · Gestes de ligne des tableaux (EPIC #1792)**, le rendu d'un menu contextuel ne se scripte pas :
longueur des libellés, lisibilité d'un item grisé, position du popup près d'un bord.

- **S4-78** · *geste: le-menu-contextuel-du-suivi-des-archives* · Clic droit sur une ligne du **suivi des archives** : le menu s'ouvre entièrement lisible, aucun
  libellé coupé.
- **S4-79** · *geste: le-menu-contextuel-du-suivi-des-archives* · L'ordre y est « Ouvrir le dossier », « Copier », puis « Colonnes… » **en dernier**.
- **S4-80** · *hors-portée: le presse-papiers ou le gestionnaire de fichiers du système, hors de l'écran* · « Ouvrir le dossier » ouvre bien le dossier `depot/` dans le gestionnaire de fichiers.
- **S4-81** · *hors-portée: le presse-papiers ou le gestionnaire de fichiers du système, hors de l'écran* · « Copier ▸ Chemin du dossier » place un chemin **collable** dans le presse-papier (vérifier en
  collant dans une barre d'adresse).
- **S4-82** · *hors-portée: le presse-papiers ou le gestionnaire de fichiers du système, hors de l'écran* · Clic droit sur une ligne de la **table de dépôt** : « Copier ▸ Identifiant » donne le nom du ZIP.
- **S4-83** · *geste: un-menu-contextuel-pres-du-bord-bas* · Clic droit sur une ligne proche du **bord bas** de l'écran : le menu s'ouvre vers le haut et reste
  entièrement visible.

**Bloc C · Import rapide et publication des corrections (#1838, exige le vrai serveur)**

Ce bloc ne s'automatise pas : il mesure des **durées réelles** contre la plateforme et vérifie qu'une
annulation interrompt vraiment un téléchargement en cours. Les tests couvrent la logique ; ils ne
peuvent pas dire si le premier import « paraît instantané » ni si « Annuler » rend la main.

- **S4-84** · *hors-portée: une durée réelle mesurée contre la plateforme. « Paraît instantané » ne veut plus rien dire quand tout l'est* · Sur la nuit analysée, « ☰ → Importer depuis Vigie-Chiro… » ramène les observations **sans fenêtre de
  progression paginée** (voie CSV) : noter la durée observée.
- **S4-85** · *prérequis: une base de départ où les observations d'une nuit sont importées par la voie CSV (générateur de bases déclarées de #4325)* · Les observations sont à l'écran, colonne « Avis validateur » **vide** (le CSV ne la porte pas).
- **S4-86** · *prérequis: une base de départ où une nuit vient d'être importée (générateur de bases déclarées de #4325)* · « ☰ → Publier les corrections… » est **actif** (non grisé) sur cette nuit tout juste importée.
- **S4-87** · *prérequis: une base de départ où des observations importées portent des corrections, les unes ancrées et les autres non (générateur de bases déclarées de #4325)* · Après avoir corrigé une observation et déclaré sa certitude, la confirmation annonce « N prête(s) à
  partir, et M à ancrer d'abord ».
- **S4-88** · *hors-portée: une pagination qui prend réellement du temps : ce que le cas note est la durée et la progression, pas le résultat* · À l'accord, une fenêtre « Récupération des identifiants et des échanges avec le validateur… (page x/y) » s'affiche et
  **progresse** : noter la durée totale.
- **S4-89** · *hors-portée: une récupération assez longue pour que « Annuler » puisse rendre la main AVANT la fin. Sans durée, il n'y a rien à interrompre* · Le bouton **Annuler** de cette fenêtre rend la main **avant** la fin, et le bandeau n'annonce aucune
  publication.
- **S4-90** · *hors-portée: des corrections réellement publiées, et un bandeau qui rend compte de ce que la plateforme en a fait* · Après une publication menée à son terme, le bandeau annonce les corrections envoyées **sans écart
  « sans ancrage »**.
- **S4-91** · *hors-portée: la fiche WEB de la plateforme, dans un navigateur. Aucun carton n'y peut rien : il remplace une ÉTAPE muette, pas l'observable lui-même* · Sur le portail Vigie-Chiro, l'observation porte le taxon et la certitude déclarés ici.
- **S4-92** · *hors-portée: une première publication réelle, sans quoi la seconde n'a aucun identifiant en base à ne pas récupérer deux fois* · Republier immédiatement : la publication repart **sans** repasser par la récupération des
  identifiants (ils sont désormais en base).
- **S4-93** · *hors-portée: le verdict d'un validateur du MNHN. Aucun dispositif ne le fabrique : il faut qu'un humain ait tranché* · « ☰ → Réimporter depuis Vigie-Chiro… » repasse, lui, par la fenêtre **paginée**, et la colonne « Avis
  validateur » se **remplit** si le MNHN a tranché.

## Constats candidats (desk-check, à confirmer en séance)

| # | Axe | Constat |
|---|---|---|
| S4-C01 | E/F | Le dépôt exige un site relié mais **rien ne le dit avant le clic** : bouton actif, archives générées, échec à la fin. À griser dès l'ouverture ou à mettre dans la checklist |
| S4-C02 | C/D | **Aucun retour arrière serveur** : « Réinitialiser » / « Annuler » sont locaux ; la doc ne dit pas que les fichiers téléversés restent en ligne ; « réinitialiser puis re-téléverser » **duplique** (avec S4-C08) |
| S4-C03 | F | Le stepper affiche **toutes les étapes franchies** dès « Déposé », alors que « Lancer la participation » reste à faire (la doc martèle « déposer ≠ faire traiter ») |
| S4-C04 | F | Cette action critique n'est **jamais mise en avant** : bouton secondaire, libellés qui disent encore « Marquer le passage déposé » |
| S4-C05 | C | La fiche affirme que « Préparer » **reste grisé** tant qu'un contrôle échoue ; le code le laisse **actif** (relançable). C'est la fiche qui est fausse |
| S4-C06 | P/E | Le choix **ZIP / WAV** n'existe **ni en IHM ni en Réglages** : l'IHM impose le ZIP, WAV n'est atteignable qu'en CLI ; or ce choix détermine si l'audio reste récupérable côté serveur (→ #1515) |
| S4-C08 | E | En dépôt ZIP, la réconciliation serveur ne fait rien (elle ne lit que les WAV) : rien n'empêche de redéposer les mêmes archives |
| S4-C09 | D | **Aucune capture** ne montre le dépôt automatique, la reprise, la carte de traitement, ni Réglages ▸ Dépôt : le harnais assemble `lot` **sans connexion**, la moitié de l'écran documenté n'est jamais rendue |
| S4-C10 | C | La doc annonce « 24 à 48 h » et l'alerte « trop long » se déclenche à > 24 h, alors que le délai réel ≈ 2 h : **S4 mesure le délai réel** pour recaler doc et seuil |

## Parité CLI (desk-check)

Bonne couverture, deux surprises : la CLI **surpasse** l'IHM (`--wav` / `--archives`, `--forcer`, et
surtout `verifier-depot-vigiechiro`, sans équivalent IHM alors que c'est le seul moyen de confirmer qu'un
dépôt ZIP est bien arrivé). Écart de nommage : la commande `deposer` ne dépose rien (elle prépare et
marque déposé, sans réseau) : confusion avec `deposer-vigiechiro`.

Un écart s'était ajouté avec ce chantier : les **métadonnées d'un passage** n'existaient que dans la
modale IHM. **Il est comblé depuis** : `metadonnees-passage` expose `--recuperer`, `--envoyer`,
`--enregistreur` et les heures, plus un rattrapage `--tout` qui n'écrit qu'avec `--confirmer`. Le
rattrapage en masse des nuits rapatriées avant #1814 ne se fait donc plus une par une.

Ce que la commande n'a **pas**, en revanche, c'est le moindre cas `bats` : ni celui de surface que
portent ses voisines, ni la vérification de ses codes de sortie, dont le `1` qu'elle rend sur un
renoncement de concurrence. Suivi en #4753.

## Prérequis avant de lancer

- Espace disque libéré (~22 Go pour le chemin ZIP) ; dépôt sur `main` ; `clean compile`.
- **Ne pas builder pendant qu'une instance tourne** (leçon S3 : une classe synthétique chargée à chaud
  peut disparaître → erreurs fantômes type `QualificationController$1`).

## Renvois

Cache d'état et « résultats à importer » → #1338 ; formulation « lot » → #1510 ; parité qualification →
#1512 ; régression de workflow → **#1514** ; confirmations génériques → #1499 ; point d'écoute non
modifiable → #1495 ; choix ZIP/WAV en Réglages → #1515.
