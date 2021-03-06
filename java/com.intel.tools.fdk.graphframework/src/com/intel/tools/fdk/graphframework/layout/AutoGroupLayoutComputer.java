/*
 * Copyright (C) 2013-2017 Intel Corporation
 *
 * This Program is subject to the terms of the Eclipse Public License, v. 1.0.
 * If a copy of the license was not distributed with this file,
 * you can obtain one at <http://www.eclipse.org/legal/epl-v10.html>
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package com.intel.tools.fdk.graphframework.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.draw2d.geometry.PrecisionPoint;

import com.intel.tools.fdk.graphframework.graph.INode;
import com.intel.tools.fdk.graphframework.graph.impl.Group;
import com.intel.tools.fdk.graphframework.graph.impl.Leaf;
import com.intel.tools.fdk.graphframework.graph.impl.NodeContainer;

/**
 * Layout algorithm which layout Group of a Graph before the entire graph. </br>
 * The AutoGroupLayoutComputer is used on each group.
 */
public class AutoGroupLayoutComputer {

    private final GraphCompacter compacter;

    private final Map<INode, PrecisionPoint> coordinates = new HashMap<>();

    public AutoGroupLayoutComputer(final NodeContainer graph) {

        this.compacter = new GraphCompacter(graph);
        computeCompactedContainer();

        if (!graph.getGroups().isEmpty()) {
            final Map<Group, AutoGroupLayoutComputer> computers = graph.getGroups().stream()
                    .collect(Collectors.toMap(Function.identity(), AutoGroupLayoutComputer::new));

            // Analyse sub group size, here order is essential to always get the same layout
            computers.keySet().stream().collect(Collectors.toCollection(TreeSet::new)).forEach(group -> {
                final Leaf compactedNode = compacter.getCompactedGroup(group);
                double xMax = 0;
                double xMin = 0;
                double yMax = 0;
                double yMin = 0;
                for (final PrecisionPoint location : computers.get(group).coordinates.values()) {
                    xMax = location.x > xMax ? location.x : xMax;
                    yMax = location.y > yMax ? location.y : yMax;
                    xMin = location.x < xMin ? location.x : xMin;
                    yMin = location.y < xMin ? location.y : yMin;
                }
                pushLeft(compactedNode, Math.abs(xMax - xMin));
                pushDown(compactedNode, Math.abs(yMax - yMin));
            });
            // Get coordinates of all nodes
            computers.forEach((group, computer) -> {
                final Leaf compactedNode = compacter.getCompactedGroup(group);
                final PrecisionPoint groupCoordinate = this.coordinates.get(compactedNode);
                this.coordinates.remove(compactedNode);
                if (!group.getLeaves().isEmpty()) {
                    computer.coordinates.keySet().forEach(leaf -> {
                        final PrecisionPoint leafCoordinate = computer.coordinates.get(leaf);
                        this.coordinates.put(leaf, new PrecisionPoint(
                                groupCoordinate.x + leafCoordinate.x, groupCoordinate.y + leafCoordinate.y));
                    });
                } else {
                    this.coordinates.put(group, groupCoordinate);
                }
            });
        }
    }

    private void computeCompactedContainer() {
        final AutoLayoutComputer computer = new AutoLayoutComputer(this.compacter.getCompactedGraph());
        computer.getCoordinates().keySet().forEach(leaf -> {
            if (!compacter.getCompactedGroups().contains(leaf)) {
                this.coordinates.put(this.compacter.getOriginalOf(leaf), computer.getCoordinates().get(leaf));
            } else {
                // Put group copies into coordinates, those nodes will be removed later
                this.coordinates.put(leaf, computer.getCoordinates().get(leaf));
            }
        });
    }

    private void pushLeft(final Leaf base, final double offset) {
        this.coordinates.keySet().forEach(leaf -> {
            if (this.coordinates.get(leaf).x >= this.coordinates.get(base).x && leaf != base) {
                this.coordinates.get(leaf).translate(offset, 0);
            }
        });
    }

    private void pushDown(final Leaf base, final double offset) {
        this.coordinates.keySet().forEach(leaf -> {
            if (this.coordinates.get(leaf).y >= this.coordinates.get(base).y && leaf != base) {
                this.coordinates.get(leaf).translate(0, offset);
            }
        });
    }

    /**
     * Retrieve coordinates of a raw instance Coordinate returned are "raw" they symbolize the position of the instance
     * on a grid of NxN (with N the number of instances in the algorithm)
     *
     * @param node
     *            the instance we want the coordinates
     * @return a point containing instance coordinate or (0, 0) if the node is unknown
     */
    public PrecisionPoint getCoordinate(final INode node) {
        if (coordinates.containsKey(node)) {
            return coordinates.get(node);
        } else {
            return new PrecisionPoint(0, 0);
        }
    }

}
