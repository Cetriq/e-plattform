'use client';

import React, { useRef, useState, useEffect } from 'react';
import type { QueryDefinition } from '../types';
import { useFormContext } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface SignatureFieldProps {
  query: QueryDefinition;
}

/**
 * Signature field component - allows user to draw a signature.
 * Uses a canvas element for drawing. People who can't draw with a mouse or
 * finger type their name instead; it is rendered onto the same canvas, so the
 * stored value is the same kind of image either way (WCAG 2.1.1).
 */
export function SignatureField({ query }: SignatureFieldProps) {
  const { values, setValue, getFieldState } = useFormContext();
  const { state } = getFieldState(query.id);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [hasSignature, setHasSignature] = useState(false);
  const [typedName, setTypedName] = useState('');
  const typedId = `${query.id}-typed`;

  const value = values[query.id] as string | undefined;
  const isDisabled = state === 'DISABLED';
  const isReadonly = state === 'READONLY';

  // Initialize canvas with existing signature
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Set canvas size
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = rect.height;

    // Set drawing style
    ctx.strokeStyle = '#000';
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    // Load existing signature if any
    if (value) {
      const img = new Image();
      img.onload = () => {
        ctx.drawImage(img, 0, 0);
        setHasSignature(true);
      };
      img.src = value;
    }
  }, [value]);

  const startDrawing = (e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>) => {
    if (isDisabled || isReadonly) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    setIsDrawing(true);
    setHasSignature(true);
    setTypedName('');

    const rect = canvas.getBoundingClientRect();
    const x = 'touches' in e ? e.touches[0].clientX - rect.left : e.clientX - rect.left;
    const y = 'touches' in e ? e.touches[0].clientY - rect.top : e.clientY - rect.top;

    ctx.beginPath();
    ctx.moveTo(x, y);
  };

  const draw = (e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>) => {
    if (!isDrawing || isDisabled || isReadonly) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const x = 'touches' in e ? e.touches[0].clientX - rect.left : e.clientX - rect.left;
    const y = 'touches' in e ? e.touches[0].clientY - rect.top : e.clientY - rect.top;

    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const stopDrawing = () => {
    if (!isDrawing) return;
    setIsDrawing(false);

    // Save signature as data URL
    const canvas = canvasRef.current;
    if (canvas) {
      const dataUrl = canvas.toDataURL('image/png');
      setValue(query.id, dataUrl);
    }
  };

  const typeSignature = (name: string) => {
    setTypedName(name);
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (!name.trim()) {
      setHasSignature(false);
      setValue(query.id, undefined);
      return;
    }
    ctx.fillStyle = '#000';
    ctx.font = 'italic 32px "Brush Script MT", "Segoe Script", cursive';
    ctx.textBaseline = 'middle';
    ctx.fillText(name, 16, canvas.height / 2, canvas.width - 32);
    setHasSignature(true);
    setValue(query.id, canvas.toDataURL('image/png'));
  };

  const clearSignature = () => {
    if (isDisabled || isReadonly) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setHasSignature(false);
    setTypedName('');
    setValue(query.id, undefined);
  };

  return (
    <FieldWrapper query={{ ...query, width: 'FULL' }}>
      <div className="space-y-2">
        {/* Canvas */}
        <div className={`relative border-2 rounded-lg ${isDisabled ? 'bg-gray-100' : 'bg-white'} ${!hasSignature ? 'border-dashed border-gray-300' : 'border-gray-200'}`}>
          <canvas
            ref={canvasRef}
            role="img"
            aria-label={hasSignature ? 'Din signatur' : 'Signaturyta, tom'}
            className={`w-full h-32 touch-none ${isDisabled || isReadonly ? 'cursor-not-allowed' : 'cursor-crosshair'}`}
            onMouseDown={startDrawing}
            onMouseMove={draw}
            onMouseUp={stopDrawing}
            onMouseLeave={stopDrawing}
            onTouchStart={startDrawing}
            onTouchMove={draw}
            onTouchEnd={stopDrawing}
          />
          {!hasSignature && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <p className="text-gray-500 text-sm" aria-hidden="true">Rita din signatur här</p>
            </div>
          )}
        </div>

        {/* Clear button */}
        {hasSignature && !isDisabled && !isReadonly && (
          <button
            type="button"
            onClick={clearSignature}
            className="text-sm text-red-700 hover:text-red-800"
          >
            Rensa signatur
          </button>
        )}

        {/* Help text */}
        <p className="text-xs text-gray-500">
          Rita din signatur med musen eller fingret, eller skriv ditt namn nedan.
        </p>

        {!isDisabled && !isReadonly && (
          <div>
            <label htmlFor={typedId} className="block text-sm font-medium text-gray-700 mb-1">
              Skriv ditt namn som signatur
            </label>
            <input
              id={typedId}
              type="text"
              autoComplete="name"
              value={typedName}
              onChange={(e) => typeSignature(e.target.value)}
              className="input"
            />
          </div>
        )}
      </div>
    </FieldWrapper>
  );
}
