import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface FaqItem {
  question: string;
  answer: string;
}

@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './faq.component.html',
  styleUrl: './faq.component.scss',
})
export class FaqComponent {
  openIndex: number | null = 0;

  readonly items: FaqItem[] = [
    {
      question: 'Qu\'est-ce que Dosya ?',
      answer:
        'Dosya est une plateforme civic tech qui centralise les démarches administratives tunisiennes avec des données structurées et vérifiées, plus un assistant IA qui répond uniquement à partir de ces données.',
    },
    {
      question: 'L\'assistant IA invente-t-il des réponses ?',
      answer:
        'Non. Dosya utilise une architecture RAG : votre question est comparée à des procédures vérifiées en base. Si aucune source fiable n\'existe, l\'assistant l\'indique clairement.',
    },
    {
      question: 'Les procédures sont-elles officielles ?',
      answer:
        'Chaque procédure publiée est liée à une source (ministère, portail officiel) et une date de vérification. Les contenus communautaires restent en brouillon jusqu\'à validation humaine.',
    },
    {
      question: 'Dosya est-il gratuit ?',
      answer:
        'Oui, la consultation des procédures et l\'assistant de base sont gratuits. Créez un compte pour sauvegarder vos démarches.',
    },
    {
      question: 'Disponible en arabe ?',
      answer:
        'Oui. Les titres et contenus clés sont disponibles en arabe, et l\'interface évolue vers un support bilingue complet FR/AR.',
    },
  ];

  toggle(index: number): void {
    this.openIndex = this.openIndex === index ? null : index;
  }
}
