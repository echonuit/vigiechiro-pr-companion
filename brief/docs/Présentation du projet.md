# Présentation du projet

## Aperçu des exigences

[VigieChiro](https://www.vigienature.fr/fr/chauves-souris) est un programme de sciences participatives porté par le **Muséum national d'Histoire naturelle** (MNHN) dans le cadre de Vigie-Nature. Il s'appuie sur un réseau de bénévoles - naturalistes amateurs ou professionnels, gestionnaires d'espaces naturels, associations - qui installent ponctuellement des **enregistreurs ultrasons** sur le terrain pour suivre l'évolution des populations de chauves-souris en France métropolitaine.

Une partie de ce réseau utilise un **Passive Recorder (PR)** open-hardware basé sur la plateforme Teensy, dont le firmware est développé en open-source ([PiBatRecorderProjects/TeensyRecorders](https://framagit.org/PiBatRecorderProjects/TeensyRecorders)). Le PR est laissé seul sur un point d'écoute pendant une nuit entière. Il s'allume au crépuscule, enregistre tout signal ultrason détecté en bande 8-120 kHz à 384 kHz d'échantillonnage, et se rendort à l'aube. Une nuit peut produire plusieurs centaines à plusieurs milliers de fichiers WAV - le [sample fourni](Expression%20du%20besoin.md#donnees-fournies) en contient 1572 - accompagnés d'un journal technique (`LogPR<sn>.txt`) et d'un journal de température / hygrométrie (`PaRec<sn>_THLog.csv`).

Une fois la nuit terminée, le possesseur du PR récupère la carte SD, **extrait les fichiers sons produits, les renomme / découpe / ralentit ×10** par l'utilisation de deux logiciels distincts ([LupasRename](https://www.lupinho.net/lupas-rename.html) pour le renommage, [Kaléidoscope](https://www.wildlifeacoustics.com/products/kaleidoscope-pro) pour le découpage et l'expansion de temps), puis dépose les enregistrements obtenus sur la plateforme VigieChiro, qui les fait passer dans le pipeline d'analyse automatique **Tadarida** (logiciel scientifique de classification développé dans le cadre du programme Vigie-Nature du MNHN). Tadarida découpe les WAV en évènements sonores, les classifie en taxons (espèces de chauves-souris, mais aussi bruit ambiant, oiseaux, mammifères terrestres et insectes), et restitue le résultat sous forme d'un **CSV d'observations**. Le possesseur du PR doit ensuite **valider ou corriger** les espèces proposées par Tadarida avant que les données ne soient consolidées dans la base nationale.

Aujourd'hui, ce travail de suivi des campagnes et de pré-validation des observations combine plusieurs outils : explorateur de fichiers, tableur, lecteur audio, plateforme VigieChiro en ligne. Il gagnerait à être unifié dans un **outil unique** pour rendre la démarche plus fluide et plus accessible.

## Objectifs principaux

L'application à développer doit permettre au **possesseur d'un Passive Recorder** de :

- **Centraliser** ses campagnes de capture dans un journal personnel : date, lieu, paramètres d'acquisition, statut (brut, envoyé à VigieChiro, résultats reçus, validé) ;
- **Importer** une nuit de capture depuis un dossier (WAV bruts + log technique + log T°/hygro) sans étape manuelle ;
- **Visualiser** les conditions de capture (graphes de température/hygrométrie, état des batteries, événements du journal technique) ;
- **Charger** les CSV de résultats Tadarida récupérés depuis VigieChiro et les croiser avec les WAV correspondants ;
- **Parcourir** les observations classifiées : tri, filtrage par taxon ou par probabilité, écoute de l'évènement sonore associé (lecture ralentie pour rendre l'ultrason audible) ;
- **Valider ou corriger** la classification proposée par Tadarida avant le retour à VigieChiro ;
- **Exporter** un fichier de validation au format attendu par VigieChiro (le CSV `…observations_Vu.csv` que la plateforme sait ré-ingérer).

L'application doit également :

- **respecter les normes d'accessibilité** afin que des utilisateurs souffrant de déficiences visuelles légères puissent l'utiliser confortablement (contraste, taille de police, raccourcis clavier) ;
- **fonctionner hors-ligne** : une nuit de terrain peut produire des giga-octets de données et l'utilisateur doit pouvoir travailler sans connexion ;
- **être portable** sur Windows, Linux et macOS sans installation système lourde.

## Le client réel : Samuel Busson (CEREMA)

> 🎯 Cette SAE n'est pas une simulation. Le commanditaire est **Samuel Busson**, doctorant écologue au [CEREMA](https://www.cerema.fr/) (équipe Climat & Territoires de demain, Département Territoire Ville et Bâtiment, Groupe Territoire, site d'Aix-en-Provence). Sa thèse porte sur l'**effet de l'éclairage public LED sur l'activité acoustique des chiroptères**, et en particulier sur l'**influence de la visibilité des sources lumineuses** sur l'activité des chauves-souris **et des insectes volants**.
>
> Une précédente campagne expérimentale, liée à un autre projet, s'est appuyée sur 13 secteurs de Seine-et-Marne et a généré **plus de 560 000 contacts chiroptères** validés via Tadarida. Pour avaler ce volume, Samuel a dû développer avec ses collègues informaticiens des scripts R / Bash de pré-traitement - efficaces mais impossibles à transmettre.
>
> Pour ses **futures campagnes**, Samuel pivote vers le **Passive Recorder Teensy** ([PiBatRecorderProjects/TeensyRecorders](https://framagit.org/PiBatRecorderProjects/TeensyRecorders)), qu'il a choisi pour sa **qualité** d'acquisition, son **ouverture** open-source et son **accessibilité** à la communauté scientifique. Mais l'écosystème logiciel du PR est rudimentaire. Le *VigieChiro PR Companion* que vous allez développer **est l'outil qui manque à Samuel et à la communauté** pour exploiter sereinement le PR.
>
> Samuel viendra **réceptionner votre démonstration** en phase 2 et donnera son avis sur l'application. Cet avis pèse dans l'évaluation au même titre que la note technique de l'équipe pédagogique. Voir sa fiche persona détaillée : [Samuel](Analyse%20et%20conception/Personas/Samuel.md).

## Construction de votre propre PR

Pour vous mettre en condition réelle (sous réserve d'avoir reçu les pièces détachées à temps), **chaque équipe assemblera son propre PR au démarrage du projet**. Cette activité (encadrée, demi-journée à une journée selon les groupes) poursuit trois objectifs :

1. **Comprendre concrètement** la chaîne complète : du capteur Teensy à la carte SD, de la carte SD au dossier de session, du dossier de session à VigieChiro.
2. **Vivre la complexité** de la récupération des fichiers, de leur renommage selon la convention `Car<carre>-<annee>-<passage>-<zone>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav`, et de la mise au format attendu par la plateforme VigieChiro.
3. **Produire vos propres données de test** complémentaires au [jeu de données fourni](Expression%20du%20besoin.md#donnees-fournies) - utiles pour challenger votre application sur des cas non anticipés.

Le matériel (kits PR Teensy, cartes SD, micros) est fourni par l'équipe pédagogique. Voir [Consignes générales](Consignes%20générales.md#materiel-fourni) et [Calendrier de travail](Calendrier%20de%20travail.md).

## Parties prenantes

| Acteur | Rôle |
|---|---|
| **Samuel Busson (CEREMA)** | **Client réel**. Exprime le besoin, réceptionne la démonstration, donne un avis qualitatif sur l'application en phase 2. |
| **Possesseur de PR** (utilisateur principal) | Naturaliste amateur ou professionnel qui exploite un PR. Il installe l'appareil sur le terrain, récupère la carte SD, importe les données dans l'application, valide les classifications, exporte vers VigieChiro. La plupart ne sont pas informaticiens : l'application doit être abordable. |
| **Plateforme VigieChiro** (système amont/aval) | Reçoit les fichiers du possesseur et restitue les CSV Tadarida. L'application n'a pas à dialoguer en direct avec la plateforme - les échanges se font par téléversement / téléchargement de fichiers. |
| **Équipe pédagogique R2.02 / R2.03** | Encadre, accompagne, évalue les livrables aux deux jalons. Donne les critères d'arbitrage en cas de doute. |

> 💡 Dans la première version, l'application reste mono-utilisateur : pas de comptes, pas de synchronisation cloud, pas de gestion d'équipe. Le possesseur de PR est seul devant son ordinateur, l'application travaille sur ses fichiers locaux.
