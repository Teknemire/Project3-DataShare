import { Component } from '@angular/core';
import { SiteHeader } from '../../shared/site-header/site-header';

@Component({
  selector: 'app-legal',
  imports: [SiteHeader],
  template: `
    <app-site-header />
    <main>
      <h1>Mentions légales et confidentialité</h1>
      <p>DataShare est un prototype de démonstration locale.</p>
      <h2>Éditeur</h2>
      <p>DataShare. L’identité juridique, l’adresse, le contact, le directeur de publication et
        les coordonnées de l’hébergeur doivent être renseignés avant toute ouverture publique.</p>
      <h2>Données et finalités</h2>
      <p>Le service traite l’email, le mot de passe sous forme hachée, les fichiers envoyés,
        leurs noms, tailles, types, tags et dates pour gérer les comptes et les transferts.
        Les mots de passe des partages sont également hachés.</p>
      <p>La base juridique envisagée pour fournir le service est l’exécution du contrat ;
        la sécurité repose sur l’intérêt légitime. Ces choix doivent être validés par l’éditeur.</p>
      <h2>Destinataires et hébergement</h2>
      <p>Les fichiers sont accessibles à toute personne disposant du lien valide et, si nécessaire,
        du mot de passe. Le contenu est stocké localement ou dans le bucket S3 configuré.
        L’exploitant doit préciser l’hébergeur, la région et les éventuels transferts hors UE.</p>
      <h2>Conservation</h2>
      <p>Le lien expire après 1 à 7 jours. La purge physique traite au plus 100 fichiers toutes
        les 15 minutes par défaut : une indisponibilité ou un retard de traitement peut prolonger
        la présence du contenu, sans réactiver le lien. Les métadonnées des transferts anonymes
        sont alors supprimées. L’historique des comptes reste jusqu’à suppression du fichier ou du compte.</p>
      <p>La suppression du compte retire ses données et ses fichiers du stockage actif.
        Les durées des sauvegardes et journaux doivent être définies par l’exploitant.</p>
      <h2>Stockage du navigateur et sécurité</h2>
      <p>Le jeton de connexion est conservé dans sessionStorage jusqu’à déconnexion ou fermeture
        de l’onglet. Aucun traceur publicitaire ou outil d’audience n’est intégré.
        L’adresse réseau sert temporairement à la limitation de débit. Les journaux applicatifs
        consignent les actions et statuts, sans mots de passe, jetons, emails ou contenu.</p>
      <h2>Vos droits</h2>
      <p>Vous pouvez supprimer vos fichiers et votre compte depuis votre espace personnel.
        Pour les demandes d’accès, rectification, effacement, limitation, opposition ou portabilité,
        le contact du responsable reste à renseigner. Une réclamation peut être adressée à la CNIL.</p>
      <p>Pour la démonstration, utilisez des comptes et fichiers de test.</p>
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: #fffaf7; color: #111; }
    main { max-width: 52rem; margin: 2rem auto; padding: 1rem; line-height: 1.6; }
    h1 { font-size: 1.8rem; } h2 { font-size: 1.25rem; margin-top: 2rem; }
  `,
})
export class Legal {}
