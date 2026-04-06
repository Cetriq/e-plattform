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
 * Uses a canvas element for drawing.
 */
export function SignatureField({ query }: SignatureFieldProps) {
  const { values, setValue, getFieldState } = useFormContext();
  const { state } = getFieldState(query.id);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [hasSignature, setHasSignature] = useState(false);

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

  const clearSignature = () => {
    if (isDisabled || isReadonly) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setHasSignature(false);
    setValue(query.id, undefined);
  };

  return (
    <FieldWrapper query={{ ...query, width: 'FULL' }}>
      <div className="space-y-2">
        {/* Canvas */}
        <div className={`relative border-2 rounded-lg ${isDisabled ? 'bg-gray-100' : 'bg-white'} ${!hasSignature ? 'border-dashed border-gray-300' : 'border-gray-200'}`}>
          <canvas
            ref={canvasRef}
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
              <p className="text-gray-400 text-sm">Rita din signatur här</p>
            </div>
          )}
        </div>

        {/* Clear button */}
        {hasSignature && !isDisabled && !isReadonly && (
          <button
            type="button"
            onClick={clearSignature}
            className="text-sm text-red-600 hover:text-red-800"
          >
            Rensa signatur
          </button>
        )}

        {/* Help text */}
        <p className="text-xs text-gray-500">
          Använd musen eller fingret för att rita din signatur ovan.
        </p>
      </div>
    </FieldWrapper>
  );
}
