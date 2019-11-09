// code by lcm
package lcm.spy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Panel that displays general data for lcm types. Viewed by double-clicking or
 * right-clicking and selecting Structure Viewer on the channel list. */
public class ObjectPanel extends JPanel {
  String name;
  Object object;
  /** time of this message's arrival */
  long utime;
  int lastwidth = 500;
  int lastheight = 100;
  JViewport scrollViewport;
  /** width in pixels of all sparklines */
  final int sparklineWidth = 150;
  /** margin around the viewport area in which we will draw graphs (in pixels) */
  final int sparklineDrawMargin = 500;
  /** section the mouse is hovering over */
  Section currentlyHoveringSection;
  /** name of the section the mouse is hovering over */
  String currentlyHoveringName;
  /** global data about all charts being displayed by lcm-spy */
  ChartData chartData;
  // array of all sparklines that are visible
  // or near visible to the user right now
  List<SparklineData> visibleSparklines = new ArrayList<>();
  boolean visibleSparklinesInitialized = false;
  // we keep track of each drawing iteration to know if the row we clicked
  // on was displayed. See SparklineData.lastDrawNumber.
  int currentDrawNumber = 0;

  class Section {
    int x0, y0, x1, y1; // bounding coordinates for sensitive area
    boolean collapsed;
    final Map<String, SparklineData> sparklines = new HashMap<>();
  }

  List<Section> sections = new ArrayList<>();

  /** Constructor for an object panel, call when the user clicks to see more
   * data about a message.
   *
   * @param name
   * name of the channel
   * @param chartData
   * global data about all charts displayed by lcm-spy */
  public ObjectPanel(String name, ChartData chartData) {
    this.name = name;
    this.chartData = chartData;
    // not using a layout manager, drawing everything ourselves
    setLayout(null);
    addMouseListener(new MyMouseAdapter());
    addMouseMotionListener(new MyMouseMotionListener());
    repaint();
  }

  /** If given a viewport, the object panel can make smart decisions to not
   * draw graphs that are currently outside of the user's view
   *
   * @param viewport
   * viewport from the JScrollPane that contains this ObjectPanel. */
  public void setViewport(JViewport viewport) {
    scrollViewport = viewport;
    scrollViewport.addChangeListener(new MyViewportChangeListener());
  }

  /** Called on mouse movement to determine if we need to highlight a line or
   * open a chart.
   *
   * @param e
   * MouseEvent to process
   *
   * @return returns true if a mouse click was consumed */
  public boolean doSparklineInteraction(MouseEvent e) {
    int y = e.getY();
    currentlyHoveringName = "";
    currentlyHoveringSection = null;
    for (SparklineData data : visibleSparklines) {
      if (data.ymin <= y && data.ymax >= y && data.lastDrawNumber == currentDrawNumber) {
        // the mouse is above this sparkline
        currentlyHoveringName = data.name;
        currentlyHoveringSection = data.section;
        return true;
      }
    }
    return false;
  }

  class PaintState {
    Color indentColors[] = new Color[] { new Color(255, 255, 255), new Color(230, 230, 255), new Color(200, 200, 255) };
    Graphics g;
    FontMetrics fm;
    JPanel panel;
    int indent_level;
    int color_level;
    int y;
    int textheight;
    int x[] = new int[4]; // tab stops
    int indentpx = 20; // pixels per indent level
    int maxwidth;
    int nextsection = 0;
    int collapse_depth = 0;

    public int beginSection(String type, String name, String value) {
      // allocate a new section number and make sure there's
      // an entry for us to use in the sections array.
      int section = nextsection++;
      Section cs;
      if (section == sections.size()) {
        cs = new Section();
        sections.add(cs);
      }
      cs = sections.get(section);
      // Some enclosing section is collapsed, exit before drawing
      // anything.
      if (collapse_depth == 0) {
        // we're not currently collapsed. Draw the header (at least.)
        beginColorBlock();
        spacer();
        Font of = g.getFont();
        g.setFont(of.deriveFont(Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        String tok = cs.collapsed ? "+" : "-";
        g.setColor(Color.white);
        g.fillRect(x[0] + indent_level * indentpx, y, 1, 1);
        g.setColor(Color.black);
        String type_split[] = type.split("\\.");
        String drawtype = type_split[type_split.length - 1];
        int type_len = fm.stringWidth(drawtype);
        int name_len = fm.stringWidth(name);
        int tok_pixidx = x[0] + indent_level * indentpx;
        int type_pixidx = x[0] + indent_level * indentpx + 10;
        g.drawString(tok, tok_pixidx, y);
        g.drawString(drawtype, type_pixidx, y);
        // set top of clicking area before
        // we might do any text wrapping
        cs.y0 = y - textheight;
        // check if type field is too long. put name on new line if yes
        if (type_pixidx + type_len > x[1])
          y += textheight;
        g.drawString(name, x[1], y);
        // check if name field is too long. put value on new line if yes
        // No need to put it on a new line if value is NULL
        if (x[1] + name_len > x[2] && value.length() > 0)
          y += textheight;
        g.drawString(value, x[2], y);
        g.setFont(of);
        final int extra_click_margin = 10; // in pixels
        // set up the coordinates where clicking will toggle whether
        // we are collapsed.
        cs.x0 = x[0];
        // only have section minimization out to the edge of the text
        if (name_len > 0)
          cs.x1 = x[1] + name_len + extra_click_margin;
        else {
          cs.x1 = type_pixidx + type_len + extra_click_margin;
        }
        cs.y1 = y;
        y += textheight;
      } else {
        // no clicking area.
        cs.x0 = 0;
        cs.x1 = 0;
        cs.y0 = 0;
        cs.y1 = 0;
      }
      // if this section is collapsed, stop drawing.
      if (sections.get(section).collapsed) {
        collapse_depth++;
      } else if (collapse_depth == 0) {
        // Only indent if this section isn't collapsed.
        indent();
      }
      return section;
    }

    @SuppressWarnings("unused")
    public void endSection(int section) {
      Section cs = sections.get(section);
      // if this section is collapsed, resume drawing.
      if (sections.get(section).collapsed) {
        collapse_depth--;
      }
      if (collapse_depth == 0) {
        unindent();
      }
      spacer();
      endColorBlock();
      spacer();
    }

    public void drawStrings(String type, String name, String value, boolean isstatic) {
      if (collapse_depth > 0)
        return;
      Font of = g.getFont();
      if (isstatic)
        g.setFont(of.deriveFont(Font.ITALIC));
      g.drawString(type, x[0] + indent_level * indentpx, y);
      g.drawString(name, x[1], y);
      g.drawString(value, x[2], y);
      y += textheight;
      g.setFont(of);
    }

    /** Draws a row for a piece of data in the message and also a sparkline
     * for that data.
     *
     * @param cls type of the data
     * @param name of the entry in the message
     * @param o the data itself
     * @param isstatic true if the data is static
     * @param sec index of section this row is in, used to determine if this
     * row should be highlighted because it is under the mouse cursor. */
    @SuppressWarnings("rawtypes")
    public void drawStringsAndGraph(Class cls, String name, Object o, boolean isstatic, int sec) {
      Section cs = sections.get(sec);
      double value = Double.NaN;
      if (o instanceof Double)
        value = (Double) o;
      else if (o instanceof Float)
        value = (Float) o;
      else if (o instanceof Integer)
        value = (Integer) o;
      else if (o instanceof Long)
        value = (Long) o;
      else if (o instanceof Short)
        value = (Short) o;
      else if (o instanceof Byte)
        value = (Byte) o;
      if (collapse_depth > 0)
        return;
      if (isstatic) {
        drawStrings(cls.getName(), name, o.toString(), isstatic);
        return;
      }
      Color oldColor = g.getColor();
      if (currentlyHoveringSection != null && cs == currentlyHoveringSection && currentlyHoveringName.equals(name)) {
        g.setColor(Color.RED);
      }
      Font of = g.getFont();
      g.drawString(cls.getName(), x[0] + indent_level * indentpx, y);
      g.drawString(name, x[1], y);
      if (cls.equals(Byte.TYPE)) {
        g.drawString(String.format("0x%02X   %03d   %+04d   %c", (o), ((Byte) o).intValue() & 0x00FF, (o), ((Byte) o) & 0xff), x[2], y);
      } else {
        g.drawString(o.toString(), x[2], y);
      }
      g.setColor(oldColor);
      // draw the graph
      if (!Double.isNaN(value)) {
        SparklineData data = cs.sparklines.get(name);
        // update the positions every loop in case another section
        // was collapsed
        data.xmin = x[3];
        data.xmax = x[3] + sparklineWidth;
        // add the data to our trace
        data.lastDrawNumber = currentDrawNumber;
      }
      y += textheight;
      g.setFont(of);
      g.setColor(oldColor);
    }

    public void spacer() {
      if (collapse_depth > 0)
        return;
      y += textheight / 2;
    }

    public void beginColorBlock() {
      if (collapse_depth > 0)
        return;
      color_level++;
      g.setColor(indentColors[color_level % indentColors.length]);
      g.fillRect(x[0] + indent_level * indentpx - indentpx / 2, y - fm.getMaxAscent(), getWidth(), getHeight());
      g.setColor(Color.black);
    }

    public void endColorBlock() {
      if (collapse_depth > 0)
        return;
      color_level--;
      g.setColor(indentColors[color_level % indentColors.length]);
      g.fillRect(x[0] + indent_level * indentpx - indentpx / 2, y - fm.getMaxAscent(), getWidth(), getHeight());
      g.setColor(Color.black);
    }

    public void indent() {
      indent_level++;
    }

    public void unindent() {
      indent_level--;
    }

    public void finish() {
      g.setColor(Color.white);
      g.fillRect(0, y, getWidth(), getHeight());
    }
  }

  public void setObject(Object o, long utime) {
    this.object = o;
    this.utime = utime - chartData.getStartTime();
    JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
    if (topFrame.getExtendedState() == Frame.ICONIFIED) {
      // ---
    } else {
      repaint();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(lastwidth, lastheight);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  /** Updates visibleSparklines to reflect the data that is near the user's
   * view at the current time.
   *
   * @param viewport
   * viewport the user is looking at. Usually from an event:
   * e.getSource() */
  void updateVisibleSparklines(JViewport viewport) {
    Rectangle view_rect = viewport.getViewRect();
    visibleSparklines.clear();
    for (int i = sections.size() - 1; i > -1; i--) {
      Section section = sections.get(i);
      if (section.collapsed == false) {
        Iterator<Entry<String, SparklineData>> it = section.sparklines.entrySet().iterator();
        while (it.hasNext()) {
          Entry<String, SparklineData> pair = it.next();
          SparklineData data = pair.getValue();
          if (data.ymin > view_rect.y - sparklineDrawMargin && //
              data.ymax < view_rect.y + view_rect.height + sparklineDrawMargin) {
            visibleSparklines.add(data);
          }
        }
      }
    }
  }

  @Override
  public void paint(Graphics g) {
    Graphics2D graphics = (Graphics2D) g;
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int width = getWidth(), height = getHeight();
    graphics.setColor(Color.white);
    graphics.fillRect(0, 0, width, height);
    graphics.setColor(Color.black);
    FontMetrics fm = g.getFontMetrics();
    PaintState ps = new PaintState();
    ps.panel = this;
    ps.g = g;
    ps.fm = fm;
    ps.textheight = 15;
    ps.y = ps.textheight;
    ps.indent_level = 1;
    ps.x[0] = 0;
    ps.x[1] = Math.min(200, width / 4);
    ps.x[2] = Math.min(ps.x[1] + 200, 2 * width / 4);
    ps.x[3] = ps.x[2] + 150;
    currentDrawNumber++;
    int previousNumSections = sections.size();
    // check to make sure that visibleSparklines has been
    // initialized otherwise we can end up not displaying
    // items if the viewport is never changed
    if (!visibleSparklinesInitialized && visibleSparklines.isEmpty() && (previousNumSections > 0) && (scrollViewport != null)) {
      visibleSparklinesInitialized = true;
      updateVisibleSparklines(scrollViewport);
    }
    if (object != null)
      paintRecurse(g, ps, "", object.getClass(), object, false, -1);
    ps.finish();
    if (ps.y != lastheight) {
      lastheight = ps.y;
      invalidate();
      getParent().validate();
    }
    if (previousNumSections != sections.size()) {
      // if the number of sections has changed, the system that figures out
      // what to draw based on user view needs to rerun to update
      repaint();
    }
  }

  @SuppressWarnings("rawtypes")
  void paintRecurse(Graphics g, PaintState ps, String name, Class cls, Object obj, boolean isstatic, int section) {
    if (obj == null) {
      ps.drawStrings(cls == null ? "(null)" : cls.getName(), name, "(null)", isstatic);
      return;
    }
    if (cls.isPrimitive() || cls.equals(Byte.TYPE)) {
      // This is our common case...
      Section cs = sections.get(section);
      SparklineData data = cs.sparklines.get(name); // if data == null,
                                                    // this graph doesn't
                                                    // exist yet
      if (data == null) {
        // we may or may not draw this depending on if it is near the
        // view but we need to keep track of it
        // so the user can click on it
        data = new SparklineData();
        data.name = name;
        data.section = cs;
        data.isHovering = false;
        // data.chart = null;
        cs.sparklines.put(name, data);
      }
      // text can drop below the expected height for letters like
      // "g", which makes it possible to click on a letter and get
      // the wrong graph. Add a small correction factor to deal with that
      final int text_below_line_height = 2; // in px
      data.ymin = ps.y - ps.textheight + text_below_line_height;
      data.ymax = ps.y + text_below_line_height;
      if (visibleSparklines.contains(data)) {
        ps.drawStringsAndGraph(cls, name, obj, isstatic, section);
      } else {
        // don't bother drawing the strings or graph for it.
        // just update the text height to pretend we drew it
        // (on huge messages, this is a large CPU savings)
        if (ps.collapse_depth > 0)
          return;
        ps.y += ps.textheight;
      }
    } else if (obj instanceof Enum) {
      ps.drawStrings(cls.getName(), name, ((Enum) obj).name(), isstatic);
    } else if (cls.equals(String.class)) {
      ps.drawStrings("String", name, obj.toString(), isstatic);
    } else if (cls.isArray()) {
      int sz = Array.getLength(obj);
      int sec = ps.beginSection(cls.getComponentType() + "[]", name + "[" + sz + "]", "");
      for (int i = 0; i < sz; i++)
        paintRecurse(g, ps, name + "[" + i + "]", cls.getComponentType(), Array.get(obj, i), isstatic, sec);
      ps.endSection(sec);
    } else {
      // it's a compound type. recurse.
      int sec = ps.beginSection(cls.getName(), name, "");
      // it's a class
      Field fs[] = cls.getFields();
      for (Field f : fs) {
        try {
          paintRecurse(g, ps, f.getName(), f.getType(), f.get(obj), isstatic || ((f.getModifiers() & Modifier.STATIC) != 0), sec);
        } catch (Exception ex) {
          System.out.println(ex.getMessage());
          ex.printStackTrace(System.out);
        }
      }
      ps.endSection(sec);
    }
  }

  @Override
  public boolean isOptimizedDrawingEnabled() {
    return false;
  }

  class MyMouseAdapter extends MouseAdapter {
    /** Handle mouse clicks. Either opens graphs if the user clicked on a row
     * or toggles sections.
     *
     * @param e
     * MouseEvent that fired this click */
    @Override
    public void mouseClicked(MouseEvent e) {
      int x = e.getX(), y = e.getY();
      // check to see if we have clicked on a row in the inspector
      // and should open a graph of the data
      if (doSparklineInteraction(e) == true) {
        return;
      }
      int bestsection = -1;
      // find the bottom-most section that contains the mouse click.
      for (int i = 0; i < sections.size(); i++) {
        Section cs = sections.get(i);
        if (x >= cs.x0 && x <= cs.x1 && y >= cs.y0 && y <= cs.y1) {
          bestsection = i;
        }
      }
      if (bestsection >= 0)
        sections.get(bestsection).collapsed ^= true;
      // when changing sections, need to recompute visibility of
      // sparklines
      // or you can end up not displaying a section until the viewport
      // changes
      updateVisibleSparklines(scrollViewport);
      // call repaint here so the UI will update immediately instead of
      // waiting for the next piece of data
      repaint();
    }
  }

  class MyMouseMotionListener extends MouseMotionAdapter {
    /** Check to see if we need to update the highlight on a row.
     *
     * @param e
     * MouseEvent from the mouse move */
    @Override
    public void mouseMoved(MouseEvent e) {
      // check to see if we are hovering over any rows of data
      doSparklineInteraction(e);
      // repaint in case the hovering changed
      repaint();
    }
  }

  class MyViewportChangeListener implements ChangeListener {
    /** Here we build a list of the items that are visible or are close to
     * visible to the user. That way, we can only update sparkline charts
     * that are close to what the user is looking at, reducing CPU load with
     * huge messages
     *
     * @param e
     * change event that fired this update */
    @Override
    public void stateChanged(ChangeEvent e) {
      JViewport viewport = (JViewport) e.getSource();
      updateVisibleSparklines(viewport);
    }
  }
}
