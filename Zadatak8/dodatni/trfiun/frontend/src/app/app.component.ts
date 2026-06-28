import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface SubmitResponse {
  submission_id: string;
  message: string;
  extracted_to: string;
  files: string[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);

  welcomeMessage = 'Učitavanje...';
  selectedFile: File | null = null;
  dragOver = false;
  loading = false;
  error = '';
  result: SubmitResponse | null = null;

  @HostListener('window:dragover', ['$event'])
  onWindowDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  @HostListener('window:drop', ['$event'])
  onWindowDrop(event: DragEvent): void {
    event.preventDefault();
  }

  ngOnInit(): void {
    this.http.get<{ message: string }>('/api/welcome').subscribe({
      next: (response) => {
        this.welcomeMessage = response.message;
      },
      error: () => {
        this.welcomeMessage = 'Server nije dostupan.';
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.setSelectedFile(file);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;

    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.setSelectedFile(file);
    }
  }

  openFilePicker(): void {
    document.getElementById('archive')?.click();
  }

  private setSelectedFile(file: File): void {
    const name = file.name.toLowerCase();
    const allowed =
      name.endsWith('.tar') || name.endsWith('.tar.gz') || name.endsWith('.tgz');

    if (!allowed) {
      this.selectedFile = null;
      this.error = 'Dozvoljene su samo .tar, .tar.gz i .tgz arhive.';
      return;
    }

    this.selectedFile = file;
    this.error = '';
    this.result = null;
  }

  submit(): void {
    if (!this.selectedFile) {
      this.error = 'Izaberite .tar arhivu pre slanja.';
      return;
    }

    const formData = new FormData();
    formData.append('archive', this.selectedFile);

    this.loading = true;
    this.error = '';
    this.result = null;

    this.http.post<SubmitResponse>('/api/submit', formData).subscribe({
      next: (response) => {
        this.result = response;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.error ?? 'Slanje nije uspelo.';
        this.loading = false;
      },
    });
  }
}
