import { Component, OnInit } from '@angular/core';

interface Particle {
  x: number;
  y: number;
  size: number;
  opacity: number;
  duration: number;
  delay: number;
  driftX: number;
  driftY: number;
  color: string;
}

@Component({
  selector: 'app-particle-background',
  templateUrl: './particle-background.component.html',
  styleUrl: './particle-background.component.scss',
  standalone: false
})
export class ParticleBackgroundComponent implements OnInit {
  particles: Particle[] = [];
  particleCount = 80;

  offsetX = 0;
  offsetY = 0;

  ngOnInit() {
    this.particles = Array.from({ length: this.particleCount }, () =>
      this.createParticle()
    );
  }

  private createParticle(): Particle {
    return {
      x: this.random(0, 100),
      y: this.random(0, 100),
      size: this.random(2, 10),
      opacity: this.random(0.4, 0.8),
      duration: this.random(12, 28),
      delay: this.random(0, 0),
      driftX: this.random(-120, 120),
      driftY: this.random(-160, 160),
      color: this.randomGlowColor()
    };
  }

  private randomGlowColor(): string {
    const colors = [
      '255,255,255', // white
      '100,220,255', // soft blue
      '210,100,255', // lavender
      '80,240,255', // cyan
      '120,110,255', // purple tint
    ];

    return colors[Math.floor(Math.random() * colors.length)];
  }

  private random(min: number, max: number): number {
    return +(Math.random() * (max - min) + min).toFixed(2);
  }
}
