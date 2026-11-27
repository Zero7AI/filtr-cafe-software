'use client';

import React from 'react';
import { Modal } from '@heroui/react';

export interface AdminModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'cover' | 'full';
  children: React.ReactNode;
}

export default function AdminModal({
  isOpen,
  onClose,
  title,
  subtitle,
  size = 'md',
  children,
}: AdminModalProps) {
  return (
    <Modal.Backdrop
      isOpen={isOpen}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      variant="blur"
      isDismissable
    >
      <Modal.Container size={size} placement="center" scroll="inside">
        <Modal.Dialog className="border border-border bg-overlay text-foreground">
          <Modal.CloseTrigger />
          <Modal.Header className="pb-2 border-b border-separator">
            <Modal.Heading className="text-base font-bold text-foreground">
              {title}
            </Modal.Heading>
            {subtitle ? (
              <p className="text-xs text-muted mt-0.5">{subtitle}</p>
            ) : null}
          </Modal.Header>
          <Modal.Body className="pt-4 text-foreground">{children}</Modal.Body>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
